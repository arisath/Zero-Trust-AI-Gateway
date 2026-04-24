# Zero-Trust AI Gateway

## Overview

A Spring Boot / Spring Cloud Gateway application that sits in front of LLM services and enforces multiple security layers on every request and response. All traffic — regardless of source — is treated as untrusted and must pass through the full filter chain before reaching the model and before any response is returned to the caller.

## Architecture

```
Client
  │
  ▼
[OAuth2 / JWT Auth]           ← prod profile only (JWKS-validated)
  │
  ▼
[TokenGuardFilter]            ← per-user request/token rate limiting (Redis)
  │
  ▼
[JailbreakClassifierFilter]   ← prompt injection / jailbreak detection
  │
  ▼
[Spring Cloud Gateway Router]
  ├─ /api/llm/**       → lb://llm-service
  └─ /api/secure-llm/** → lb://secure-llm
  │
  ▼
[Downstream LLM / Ollama]
  │
  ▼
[PiiRedactorFilter]           ← PII redaction on the response
  │
  ▼
Client
```

A direct `/api/chat` endpoint is also available, which calls Ollama directly (bypassing the proxy routes) and applies the same jailbreak and PII logic inline.

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.2.4 + Spring Cloud Gateway (WebFlux / Netty) |
| Security | Spring Security OAuth2 Resource Server + JWT (JWKS) |
| LLM | Ollama (native `/api/chat` endpoint) |
| Rate limiting | Redis Reactive (sliding-window, per-user) |
| Resilience | Resilience4j circuit breaker |
| Observability | Spring Boot Actuator + Micrometer + Prometheus |
| Containers | Docker (OpenJDK 17 slim) |

## Project Structure

```
ai-gateway/
├── src/main/java/com/securellm/
│   ├── GatewayApplication.java
│   ├── config/
│   │   ├── GatewayConfig.java          # routes + filter chain wiring
│   │   ├── SecurityConfig.java         # JWT / JWKS auth (prod profile)
│   │   ├── LocalSecurityConfig.java    # auth disabled (local profile)
│   │   └── RedisConfig.java            # ReactiveRedisTemplate bean
│   ├── controller/
│   │   ├── ChatController.java         # POST /api/chat (direct Ollama call)
│   │   └── ConnectivityController.java # GET  /api/v1/check-connectivity
│   ├── filter/
│   │   ├── TokenGuardFilter.java
│   │   ├── JailbreakClassifierFilter.java
│   │   └── PiiRedactorFilter.java
│   └── service/
│       ├── LlmService.java             # Ollama WebClient wrapper
│       ├── JailbreakDetectionService.java
│       ├── PiiDetectionService.java
│       └── TokenUsageService.java
└── src/main/resources/
    ├── application.yaml                # base config (all profiles)
    └── application-prod.yaml           # prod-only: JWT issuer / JWKS URI
```

## Security Layers

### 1. Authentication (prod only)
JWT Bearer tokens validated against your identity provider's JWKS endpoint. Actuator health/metrics paths are public. Disabled entirely in the `local` profile.

### 2. TokenGuardFilter
Enforces two independent Redis-backed sliding-window limits per user identity (resolved from JWT principal or `X-User-Id` header):
- **Requests per minute** — returns `429` with `Retry-After: 60`
- **Estimated tokens per hour** — returns `429` with `Retry-After: 3600`

Fails open (passes traffic) if Redis is unavailable.

### 3. JailbreakClassifierFilter
Buffers the request body and scans it against 14 regex patterns covering:
- Instruction-override phrases ("ignore all previous instructions", "forget your instructions")
- DAN / unrestricted-AI role-play
- Safety/restriction bypass attempts
- Raw model tokens injected into user input (`[INST]`, `<|im_start|>`, etc.)

Returns `403 Forbidden` on a match. Fails open on read errors.

### 4. PiiRedactorFilter
Intercepts the response body (text/JSON content types only) and redacts:

| Pattern | Placeholder |
|---|---|
| Email addresses | `[REDACTED-EMAIL]` |
| US Social Security Numbers | `[REDACTED-SSN]` |
| Credit / debit card numbers | `[REDACTED-CARD]` |
| US phone numbers | `[REDACTED-PHONE]` |
| IPv4 addresses | `[REDACTED-IP]` |
| US ZIP codes | `[REDACTED-ZIP]` |

## Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/chat` | Direct chat via Ollama — `{"prompt": "..."}` |
| `ANY` | `/api/llm/**` | Proxied to `lb://llm-service` (full filter chain) |
| `ANY` | `/api/secure-llm/**` | Proxied to `lb://secure-llm` (full filter chain) |
| `GET` | `/api/v1/check-connectivity` | Outbound connectivity health check |
| `GET` | `/actuator/health` | Application health |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

## Configuration

### Environment variables

| Variable | Default | Description |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL |
| `OLLAMA_MODEL` | `gemma3:1b` | Model name (must be pulled in Ollama) |
| `OLLAMA_TIMEOUT_SECONDS` | `120` | Request timeout |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | _(empty)_ | Redis password |
| `MAX_REQUESTS_PER_MINUTE` | `60` | Per-user request rate limit |
| `MAX_TOKENS_PER_HOUR` | `100000` | Per-user token rate limit |
| `JAILBREAK_DETECTION_ENABLED` | `true` | Toggle jailbreak filter |
| `PII_REDACTION_ENABLED` | `true` | Toggle PII redaction filter |
| `JWT_ISSUER_URI` | _(required in prod)_ | OIDC issuer URI |
| `JWT_JWKS_URI` | _(required in prod)_ | JWKS endpoint URL |

### Profiles

| Profile | Auth | Use case |
|---|---|---|
| `local` (default) | Disabled — all requests permitted | Local development |
| `prod` | JWT required (JWKS-validated) | Production deployment |

## Prerequisites

- Java 17
- Maven 3.x
- [Ollama](https://ollama.com) running locally with at least one model pulled
- Redis (required for rate limiting; gateway starts without it but rate limiting fails open)

## Build and Run

```bash
# Pull a model into Ollama first
ollama pull gemma3:1b

# Build
mvn clean package -q

# Run (local profile — auth disabled)
mvn spring-boot:run

# Run with a different model
OLLAMA_MODEL=mistral mvn spring-boot:run

# Run in production mode
SPRING_PROFILES_ACTIVE=prod \
  JWT_ISSUER_URI=https://auth.example.com \
  JWT_JWKS_URI=https://auth.example.com/.well-known/jwks.json \
  mvn spring-boot:run
```

### Docker

```bash
docker build -t ai-gateway .
docker run -p 8081:8080 \
  -e OLLAMA_BASE_URL=http://host.docker.internal:11434 \
  ai-gateway
```

## Example request

```bash
curl -X POST http://localhost:8081/api/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Explain zero-trust security in one paragraph."}'
```

## License

MIT License
