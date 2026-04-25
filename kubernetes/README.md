# Kubernetes

Kustomize-based manifests to deploy the full stack to any Kubernetes cluster.

## Structure

```
kubernetes/
├── kustomization.yaml
├── namespace.yaml
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

Edit the placeholder values in `kubernetes/auth-service/secret.yaml` and `kubernetes/ai-gateway/secret.yaml` before applying. Do not commit real credentials — use Sealed Secrets, Vault, or your cloud provider's secrets manager instead.

**4. Apply everything**

```bash
kubectl apply -k kubernetes/
```

**5. Pull models into Ollama**

```bash
kubectl -n ai-gateway exec deploy/ollama -- ollama pull gemma3:1b
kubectl -n ai-gateway exec deploy/ollama -- ollama pull codellama:7b
kubectl -n ai-gateway exec deploy/ollama -- ollama pull llama3.2:3b
```

## Verify

```bash
kubectl -n ai-gateway get pods
kubectl -n ai-gateway logs deploy/ai-gateway
kubectl -n ai-gateway logs deploy/auth-service
```

## Tear Down

```bash
kubectl delete -k kubernetes/
```

## Notes

- **Ingress** — `ai-gateway/ingress.yaml` requires an Ingress controller (e.g. ingress-nginx). Update the `host:` field to your domain. TLS configuration is commented out — uncomment and set `secretName` to enable HTTPS.
- **GPU** — Ollama GPU support is commented out in `ollama/deployment.yaml`. Uncomment `resources.limits` and `nodeSelector` if your cluster has GPU nodes with the NVIDIA device plugin.
- **Autoscaling** — The HPA scales the gateway between 2–10 replicas on CPU ≥ 70% or memory ≥ 80%.
- **Persistent storage** — Redis uses a 1 Gi PVC; Ollama uses 30 Gi (models are large). Adjust in the respective `pvc.yaml` files for your storage class.
