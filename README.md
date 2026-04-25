# Zero-Trust AI Gateway

A secure, multi-module platform that sits in front of LLM services and enforces authentication, rate limiting, prompt security, and PII redaction on every request. All traffic is treated as untrusted regardless of origin.

## Architecture

![Sequence diagram — POST /api/chat request flow](img/sequence-diagram.svg)

```
Browser / API client
        │
        ▼
┌─────────────────────┐
│     auth-service    │  Issues JWT access tokens (port 9000)
│  Spring Auth Server │
└─────────────────────┘
        │  Bearer token
        ▼
┌─────────────────────┐
│     ai-gateway      │  Validates tokens, enforces security, routes to LLM (port 8081)
│  Spring Cloud GW    │──── Redis (rate limiting)
└─────────────────────┘──── Ollama (LLM inference)
```

## Modules

| Module | Port | Description |
|---|---|---|
| [`auth-service`](auth-service/README.md) | 9000 | OAuth2 Authorization Server — issues and signs JWT tokens |
| [`ai-gateway`](ai-gateway/README.md) | 8081 | Secure gateway — JWT validation, rate limiting, jailbreak detection, PII redaction, query classification, LLM routing |

## Tech Stack

| Concern | Technology |
|---|---|
| Framework | Spring Boot 3.2.4 + Spring Cloud Gateway (WebFlux / Netty) |
| Auth server | Spring Authorization Server |
| Token validation | Spring Security OAuth2 Resource Server (JWKS) |
| LLM | Ollama |
| Rate limiting | Redis Reactive |
| Observability | Spring Boot Actuator + Micrometer + Prometheus |
| Containers | Docker / Kubernetes |

## Quick Start

### Docker Compose

```bash
# 1. Start all services (gateway + auth + Redis + Ollama)
docker compose up --build

# 2. Pull at least the classifier model into Ollama
docker compose exec ollama ollama pull gemma3:1b

# 3. Gateway is ready at http://localhost:8081
#    Auth server is ready at http://localhost:9000
```

By default the gateway runs in `local` profile (auth disabled). To enable JWT enforcement:

```bash
SPRING_PROFILES_ACTIVE=prod docker compose up --build
```

Then get a token from the auth service and use it:

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

### Kubernetes

See [`kubernetes/`](kubernetes/README.md) for full deployment instructions.

## Repository Structure

```
.
├── auth-service/        OAuth2 Authorization Server
├── ai-gateway/          Secure LLM Gateway
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
