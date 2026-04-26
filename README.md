# Zero-Trust AI Gateway

A secure, multi-module platform that sits in front of LLM services and enforces authentication, authorization, rate limiting, prompt security, and PII redaction on every request. All traffic is treated as untrusted regardless of origin.

## Architecture

![Sequence diagram — POST /api/chat request flow](img/sequence-diagram.svg)

```
Browser / API client
        │
        ▼
┌─────────────────────┐
│     OpenLDAP        │  User directory — free / premium group membership (port 1389)
└─────────────────────┘
        │  user login
        ▼
┌─────────────────────┐
│     auth-service    │  Issues JWT access tokens with tier claim (port 9000)
│  Spring Auth Server │
└─────────────────────┘
        │  Bearer token (includes "tier": "free" | "premium")
        ▼
┌─────────────────────┐
│     ai-gateway      │  Validates tokens, enforces security, routes to LLM (port 8081)
│  Spring Cloud GW    │──── Redis (tier-aware rate limiting)
└─────────────────────┘──── Ollama (LLM inference)
```

## Modules

| Module | Port | Description |
|---|---|---|
| [`auth-service`](auth-service/README.md) | 9000 | OAuth2 Authorization Server — authenticates users via LDAP, issues signed JWTs with tier claim |
| [`ai-gateway`](ai-gateway/README.md) | 8081 | Secure gateway — JWT validation, tier-based rate limiting, jailbreak detection, PII redaction, query classification, LLM routing |

## Tech Stack

| Concern | Technology |
|---|---|
| Framework | Spring Boot 3.2.4 + Spring Cloud Gateway (WebFlux / Netty) |
| Auth server | Spring Authorization Server |
| User directory | OpenLDAP (bitnami/openldap) |
| Token validation | Spring Security OAuth2 Resource Server (JWKS) |
| LLM | Ollama |
| Rate limiting | Redis Reactive (per-tier limits) |
| Observability | Spring Boot Actuator + Micrometer + Prometheus |
| Containers | Docker / Kubernetes |

## Quick Start

### Docker Compose

```bash
# 1. Start all services (gateway + auth + LDAP + Redis + Ollama)
docker compose up --build

# 2. Pull the classifier model into Ollama
docker compose exec ollama ollama pull gemma3:1b

# 3. Services are ready:
#    Gateway:       http://localhost:8081
#    Auth server:   http://localhost:9000
#    phpLDAPadmin:  http://localhost:8090
```

By default the gateway runs in `local` profile (auth disabled). To enable JWT enforcement:

```bash
SPRING_PROFILES_ACTIVE=prod docker compose up --build
```

#### Getting a token (service-to-service)

```bash
TOKEN=$(curl -s -X POST http://localhost:9000/oauth2/token \
  -u ai-gateway-client:secret \
  -d "grant_type=client_credentials&scope=gateway.read" \
  | jq -r .access_token)

curl http://localhost:8081/api/chat \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Explain zero-trust security."}'
```

#### Logging in as a user (authorization-code flow)

Two seed users are provisioned in LDAP automatically:

| Username | Password | Tier |
|---|---|---|
| `alice` | `alice123` | free |
| `bob` | `bob123` | premium |

Log in at `http://localhost:9000/login`. The resulting JWT will contain `"tier": "free"` or `"tier": "premium"`, which the gateway uses to apply the appropriate rate limits.

### Kubernetes

See [`kubernetes/`](kubernetes/README.md) for full deployment instructions.

## User Tiers

| Tier | Requests / minute | Tokens / hour |
|---|---|---|
| free | 10 | 10,000 |
| premium | 60 | 100,000 |

Tier is determined by LDAP group membership (`cn=free` or `cn=premium` under `ou=groups`). Add a user to the `premium` group in phpLDAPadmin to upgrade them.

## Managing Users

phpLDAPadmin is available at `http://localhost:8090`.

- Login DN: `cn=admin,dc=securellm,dc=com`
- Password: `adminpassword` (override with `LDAP_ADMIN_PASSWORD`)

To add a new premium user, create a user under `ou=users,dc=securellm,dc=com` and add them as a `member` of `cn=premium,ou=groups,dc=securellm,dc=com`.

## Repository Structure

```
.
├── auth-service/        OAuth2 Authorization Server + LDAP integration
├── ai-gateway/          Secure LLM Gateway
├── ldap/
│   └── bootstrap.ldif   Groups OU + seed free/premium group definitions
├── kubernetes/          Kubernetes manifests (Kustomize)
├── docker-compose.yml   Local multi-service orchestration
└── pom.xml              Maven parent POM
```

## Building

```bash
# Build and test all modules from the root
mvn verify
```

## License

MIT License
