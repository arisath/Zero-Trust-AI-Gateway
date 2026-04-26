# Kubernetes

Kustomize-based manifests to deploy the full stack to any Kubernetes cluster.

## Structure

```
kubernetes/
├── kustomization.yaml
├── namespace.yaml
├── openldap/        secret · deployment · service · configmap (bootstrap LDIF)
├── auth-service/    secret · deployment · service
├── redis/           pvc · deployment · service
├── ollama/          pvc · deployment · service
└── ai-gateway/      configmap · secret · deployment · service · ingress · hpa
```

## Prerequisites

- `kubectl` v1.14+ (Kustomize is built in)
- Images pushed to a registry accessible by the cluster

## Deploy

**1. Build and push images**

```bash
docker build -t ghcr.io/your-org/ai-gateway:latest .   -f ai-gateway/Dockerfile
docker build -t ghcr.io/your-org/auth-service:latest . -f auth-service/Dockerfile
docker push ghcr.io/your-org/ai-gateway:latest
docker push ghcr.io/your-org/auth-service:latest
```

**2. Update image references**

```bash
sed -i 's|image: ai-gateway:latest|image: ghcr.io/your-org/ai-gateway:latest|'   kubernetes/ai-gateway/deployment.yaml
sed -i 's|image: auth-service:latest|image: ghcr.io/your-org/auth-service:latest|' kubernetes/auth-service/deployment.yaml
```

**3. Set secrets**

Edit the placeholder values in the following files before applying. Do not commit real credentials — use Sealed Secrets, Vault, or your cloud provider's secrets manager instead.

- `kubernetes/openldap/secret.yaml` — LDAP admin password, seed user passwords
- `kubernetes/auth-service/secret.yaml` — OAuth2 client secret, LDAP manager password
- `kubernetes/ai-gateway/secret.yaml` — JWT issuer/JWKS URIs

**4. Apply everything**

```bash
kubectl apply -k kubernetes/
```

**5. Pull models into Ollama**

```bash
kubectl -n ai-gateway exec deploy/ollama -- ollama pull gemma3:1b
kubectl -n ai-gateway exec deploy/ollama -- ollama pull qwen2.5-coder:1.5b
kubectl -n ai-gateway exec deploy/ollama -- ollama pull deepseek-r1:1.5b
```

## Verify

```bash
kubectl -n ai-gateway get pods
kubectl -n ai-gateway logs deploy/ai-gateway
kubectl -n ai-gateway logs deploy/auth-service
kubectl -n ai-gateway logs deploy/openldap
```

## Managing Users

Once deployed, manage users and group membership via `ldapsearch`/`ldapadd` against the OpenLDAP service, or deploy phpLDAPadmin as an optional admin pod.

To upgrade a user from free to premium, add them as a `member` of `cn=premium,ou=groups,dc=securellm,dc=com`:

```bash
kubectl -n ai-gateway exec deploy/openldap -- \
  ldapmodify -H ldap://localhost:1389 \
    -D "cn=admin,dc=securellm,dc=com" -w "$LDAP_ADMIN_PASSWORD" <<EOF
dn: cn=premium,ou=groups,dc=securellm,dc=com
changetype: modify
add: member
member: cn=newuser,ou=users,dc=securellm,dc=com
EOF
```

## Tear Down

```bash
kubectl delete -k kubernetes/
```

## Notes

- **LDAP** — OpenLDAP is deployed as a single-replica stateful workload. For HA, consider a replicated LDAP setup or an external managed directory service (Azure AD, AWS Directory Service, Google Cloud LDAP).
- **Ingress** — `ai-gateway/ingress.yaml` requires an Ingress controller (e.g. ingress-nginx). Update the `host:` field to your domain. TLS configuration is commented out — uncomment and set `secretName` to enable HTTPS.
- **GPU** — Ollama GPU support is commented out in `ollama/deployment.yaml`. Uncomment `resources.limits` and `nodeSelector` if your cluster has GPU nodes with the NVIDIA device plugin.
- **Autoscaling** — The HPA scales the gateway between 2–10 replicas on CPU ≥ 70% or memory ≥ 80%.
- **Persistent storage** — Redis uses a 1 Gi PVC; Ollama uses 30 Gi (models are large); OpenLDAP uses a small PVC for the directory database. Adjust in the respective `pvc.yaml` files for your storage class.
