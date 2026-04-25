# ai-gateway

Spring Cloud Gateway application that enforces multiple security layers on every LLM request and response. Classifies incoming queries and routes them to the appropriate specialist model.

## How It Works

Every request through `POST /api/chat` passes this pipeline:

```
Prompt
  │
  ▼
Blocklist check          → 403 if matched
  │
  ▼
Jailbreak detection      → 403 if matched
  │
  ▼
PII redaction (prompt)
  │
  ▼
Query classification     ← small model (gemma3:1b) assigns a category
  │
  ▼
Model routing            ← maps category → specialist Ollama model
  │
  ▼
LLM call
  │
  ▼
PII redaction (response)
  │
  ▼
{ "response": "...", "category": "PROGRAMMING" }
```

Proxied routes (`/api/llm/**`, `/api/secure-llm/**`) pass through the Spring Cloud Gateway filter chain: JWT auth → rate limiting → jailbreak detection → PII redaction on response.

## Security Layers

### 1. Authentication
JWT Bearer tokens validated against the auth-service JWKS endpoint. Active in `prod` profile only. The `local` profile disables auth entirely for development.

### 2. Rate Limiting (TokenGuardFilter)
Two independent Redis-backed sliding-window limits per user (resolved from JWT principal or `X-User-Id` header):
- **Requests per minute** — `429` with `Retry-After: 60`
- **Estimated tokens per hour** — `429` with `Retry-After: 3600`

Fails open if Redis is unavailable.

### 3. Jailbreak Detection (JailbreakClassifierFilter)
Scans the request body against 14 regex patterns covering:
- Instruction-override phrases ("ignore all previous instructions")
- DAN / unrestricted-AI role-play
- Safety/restriction bypass attempts
- Raw model tokens (`[INST]`, `<|im_start|>`, etc.)

Returns `403 Forbidden` on a match.

### 4. PII Redaction (PiiRedactorFilter)
Redacts sensitive patterns from both prompts and responses:

| Pattern | Placeholder |
|---|---|
| Email addresses | `[REDACTED-EMAIL]` |
| US Social Security Numbers | `[REDACTED-SSN]` |
| Credit / debit card numbers | `[REDACTED-CARD]` |
| US phone numbers | `[REDACTED-PHONE]` |
| IPv4 addresses | `[REDACTED-IP]` |
| US ZIP codes | `[REDACTED-ZIP]` |

## Query Classification & Model Routing

Queries are classified by a small model before being forwarded to the right specialist:

| Category | Default model | Example queries |
|---|---|---|
| `PROGRAMMING` | `codellama:7b` | "Write a binary search in Java", "Debug this Python script" |
| `MATHEMATICS` | `qwen2.5-math:7b` | "Solve x² + 2x − 3 = 0", "Prove that √2 is irrational" |
| `HISTORY` | `llama3.2:3b` | "Why did the Roman Empire fall?" |
| `SCIENCE` | `llama3.2:3b` | "How does photosynthesis work?" |
| `CREATIVE_WRITING` | `mistral:7b` | "Write a short story about a lighthouse" |
| `LEGAL` | `llama3.2:3b` | "What is the difference between civil and criminal law?" |
| `MEDICAL` | `llama3.2:3b` | "What are the symptoms of appendicitis?" |
| `GENERAL` | `gemma3:1b` | Anything that does not fit the above |

Classification failures always fall back to `GENERAL` — they never block the request.

## Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/chat` | Direct chat — `{"prompt": "..."}` → `{"response": "...", "category": "..."}` |
| `ANY` | `/api/llm/**` | Proxied to `lb://llm-service` (full filter chain) |
| `ANY` | `/api/secure-llm/**` | Proxied to `lb://secure-llm` (full filter chain) |
| `GET` | `/api/v1/check-connectivity` | Outbound connectivity health check |
| `GET` | `/actuator/health` | Application health |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

## Configuration

| Variable | Default | Description |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL |
| `OLLAMA_CLASSIFIER_MODEL` | `gemma3:1b` | Model used to classify queries |
| `OLLAMA_MODEL_PROGRAMMING` | `codellama:7b` | Model for programming queries |
| `OLLAMA_MODEL_MATHEMATICS` | `qwen2.5-math:7b` | Model for mathematics queries |
| `OLLAMA_MODEL_HISTORY` | `llama3.2:3b` | Model for history queries |
| `OLLAMA_MODEL_SCIENCE` | `llama3.2:3b` | Model for science queries |
| `OLLAMA_MODEL_CREATIVE_WRITING` | `mistral:7b` | Model for creative writing queries |
| `OLLAMA_MODEL_LEGAL` | `llama3.2:3b` | Model for legal queries |
| `OLLAMA_MODEL_MEDICAL` | `llama3.2:3b` | Model for medical queries |
| `OLLAMA_MODEL_GENERAL` | `gemma3:1b` | Model for general / unclassified queries |
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
| `local` (default) | Disabled | Local development |
| `prod` | JWT required (JWKS-validated) | Production |

## Running Locally

Prerequisites: Java 17, Maven 3.x, Ollama running, Redis running.

```bash
# Pull at least one model
ollama pull gemma3:1b

# Run (auth disabled)
mvn spring-boot:run

# Run in prod mode (JWT enforced — auth-service must be running)
SPRING_PROFILES_ACTIVE=prod \
  JWT_ISSUER_URI=http://localhost:9000 \
  JWT_JWKS_URI=http://localhost:9000/oauth2/jwks \
  mvn spring-boot:run
```

## Example Requests

```bash
curl -X POST http://localhost:8081/api/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Write a quicksort algorithm in Python."}'
```

```json
{
  "response": "def quicksort(arr): ...",
  "category": "PROGRAMMING"
}
```

## Running Tests

```bash
mvn test -pl ai-gateway
```

## Project Structure

```
src/main/java/com/securellm/
├── config/
│   ├── GatewayConfig.java            routes + filter chain wiring
│   ├── SecurityConfig.java           JWT / JWKS auth (prod profile)
│   └── LocalSecurityConfig.java      auth disabled (local profile)
├── controller/
│   ├── ChatController.java           POST /api/chat
│   └── ConnectivityController.java   GET /api/v1/check-connectivity
├── filter/
│   ├── TokenGuardFilter.java
│   ├── JailbreakClassifierFilter.java
│   └── PiiRedactorFilter.java
└── service/
    ├── LlmService.java               Ollama WebClient wrapper
    ├── QueryClassifierService.java   classifies prompt into a category
    ├── ModelRoutingService.java      maps category → model name
    ├── QueryCategory.java            enum of supported categories
    ├── JailbreakDetectionService.java
    ├── PiiDetectionService.java
    └── TokenUsageService.java
```
