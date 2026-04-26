# ai-gateway

Spring Cloud Gateway application that enforces multiple security layers on every LLM request and response. Classifies incoming queries and routes them to the appropriate specialist model. Applies tier-aware rate limits based on the user's JWT claims.

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
PII tokenization         ← replaces PII with __PII_EMAIL_1__ style tokens
  │                         stores token → original value map for this request
  ▼
Query classification     ← small model (gemma3:1b) assigns a category
  │
  ▼
Model routing            ← maps category → specialist Ollama model
  │
  ▼
LLM call                 ← receives tokenized prompt (no raw PII)
  │
  ▼
PII detokenization       ← restores original values from token map
  │
  ▼
{ "response": "...", "category": "PROGRAMMING" }
```

Proxied routes (`/api/llm/**`, `/api/secure-llm/**`) pass through the Spring Cloud Gateway filter chain: JWT auth → tier-aware rate limiting → jailbreak detection.

## Security Layers

### 1. Authentication
JWT Bearer tokens validated against the auth-service JWKS endpoint. Active in `prod` profile only. The `local` profile disables auth entirely for development.

### 2. Tier-Based Rate Limiting (TokenGuardFilter)
Two independent Redis-backed sliding-window limits per user, with limits that differ by tier:

| Tier | Requests / minute | Tokens / hour |
|---|---|---|
| free | 10 | 10,000 |
| premium | 60 | 100,000 |

Tier is read from the `tier` JWT claim (set by the auth service at login from LDAP group membership). Defaults to `free` when no JWT is present (local profile or service-to-service calls).

User identity is resolved from the JWT principal name, falling back to the `X-User-Id` header, then `anonymous`.

Both limits return `429` with an appropriate `Retry-After` header. Fails open if Redis is unavailable.

### 3. Jailbreak Detection (JailbreakClassifierFilter)
Scans the request body against 14 regex patterns covering:
- Instruction-override phrases ("ignore all previous instructions")
- DAN / unrestricted-AI role-play
- Safety/restriction bypass attempts
- Raw model tokens (`[INST]`, `<|im_start|>`, etc.)

Returns `403 Forbidden` on a match.

### 4. PII Tokenization (PiiDetectionService)
Sensitive patterns in the prompt are replaced with reversible tokens before the text reaches the LLM. After the LLM responds, the original values are restored from the per-request token map. The LLM never sees raw PII.

| Pattern | Token format |
|---|---|
| Email addresses | `__PII_EMAIL_N__` |
| US Social Security Numbers | `__PII_SSN_N__` |
| Credit / debit card numbers | `__PII_CARD_N__` |
| US phone numbers (separator required) | `__PII_PHONE_N__` |
| IPv4 addresses | `__PII_IP_N__` |
| US ZIP+4 codes | `__PII_ZIP_N__` |

`N` is a per-type counter so multiple occurrences of the same PII type in one prompt are each given a distinct token.

## Query Classification & Model Routing

Queries are classified by a small model before being forwarded to the right specialist:

| Category | Default model | Size | Example queries |
|---|---|---|---|
| `PROGRAMMING` | `qwen2.5-coder:1.5b` | ~1.0 GiB | "Write a binary search in Java", "Debug this Python script" |
| `MATHEMATICS` | `deepseek-r1:1.5b` | ~1.04 GiB | "Solve x² + 2x − 3 = 0", "Prove that √2 is irrational" |
| `HISTORY` | `deepseek-r1:1.5b` | ~1.04 GiB | "Why did the Roman Empire fall?" |
| `SCIENCE` | `deepseek-r1:1.5b` | ~1.04 GiB | "How does photosynthesis work?" |
| `CREATIVE_WRITING` | `gemma3:1b` | ~815 MiB | "Write a short story about a lighthouse" |
| `LEGAL` | `gemma3:1b` | ~815 MiB | "What is the difference between civil and criminal law?" |
| `MEDICAL` | `gemma3:1b` | ~815 MiB | "What are the symptoms of appendicitis?" |
| `GENERAL` | `gemma3:1b` | ~815 MiB | Anything that does not fit the above |

Three models are loaded into Ollama: `gemma3:1b`, `qwen2.5-coder:1.5b`, and `deepseek-r1:1.5b`. All are kept resident in memory (`OLLAMA_KEEP_ALIVE=-1`) to avoid cold-load latency between requests.

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
| `SPRING_PROFILES_ACTIVE` | `local` | `local` disables auth; `prod` enforces JWT |
| `JWT_ISSUER_URI` | _(required in prod)_ | OIDC issuer URI |
| `JWT_JWKS_URI` | _(required in prod)_ | JWKS endpoint URL |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL |
| `OLLAMA_CLASSIFIER_MODEL` | `gemma3:1b` | Model used to classify queries |
| `OLLAMA_MODEL_PROGRAMMING` | `qwen2.5-coder:1.5b` | Model for programming queries |
| `OLLAMA_MODEL_MATHEMATICS` | `deepseek-r1:1.5b` | Model for mathematics queries |
| `OLLAMA_MODEL_HISTORY` | `deepseek-r1:1.5b` | Model for history queries |
| `OLLAMA_MODEL_SCIENCE` | `deepseek-r1:1.5b` | Model for science queries |
| `OLLAMA_MODEL_CREATIVE_WRITING` | `gemma3:1b` | Model for creative writing queries |
| `OLLAMA_MODEL_LEGAL` | `gemma3:1b` | Model for legal queries |
| `OLLAMA_MODEL_MEDICAL` | `gemma3:1b` | Model for medical queries |
| `OLLAMA_MODEL_GENERAL` | `gemma3:1b` | Model for general / unclassified queries |
| `OLLAMA_TIMEOUT_SECONDS` | `300` | Request timeout (local CPU inference can be slow) |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | _(empty)_ | Redis password |
| `FREE_MAX_REQUESTS_PER_MINUTE` | `10` | Request rate limit for free-tier users |
| `FREE_MAX_TOKENS_PER_HOUR` | `10000` | Token rate limit for free-tier users |
| `PREMIUM_MAX_REQUESTS_PER_MINUTE` | `60` | Request rate limit for premium-tier users |
| `PREMIUM_MAX_TOKENS_PER_HOUR` | `100000` | Token rate limit for premium-tier users |
| `JAILBREAK_DETECTION_ENABLED` | `true` | Toggle jailbreak filter |
| `PII_REDACTION_ENABLED` | `true` | Toggle PII tokenization (prompt) and detokenization (response) |

### Profiles

| Profile | Auth | Use case |
|---|---|---|
| `local` (default) | Disabled | Local development |
| `prod` | JWT required (JWKS-validated) | Production |

## Running Locally

Prerequisites: Java 17, Maven 3.x, Ollama running, Redis running.

```bash
# Pull the three required models
ollama pull gemma3:1b
ollama pull qwen2.5-coder:1.5b
ollama pull deepseek-r1:1.5b

# Run (auth disabled)
mvn spring-boot:run

# Run in prod mode (JWT enforced — auth-service + OpenLDAP must be running)
SPRING_PROFILES_ACTIVE=prod \
  JWT_ISSUER_URI=http://localhost:9000 \
  JWT_JWKS_URI=http://localhost:9000/oauth2/jwks \
  mvn spring-boot:run
```

Prefer Docker Compose for local development — see the root README for the two startup modes (with and without auth).

## Example Requests

```bash
# Local profile — no token needed
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

```bash
# Prod profile — obtain token first (e.g. as bob, who is premium)
TOKEN=$(curl -s -X POST http://localhost:9000/oauth2/token \
  -u ai-gateway-client:secret \
  -d "grant_type=client_credentials&scope=gateway.read" \
  | jq -r .access_token)

curl -X POST http://localhost:8081/api/chat \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Explain the Riemann hypothesis."}'
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
│   ├── TokenGuardFilter.java         tier-aware rate limiting
│   ├── JailbreakClassifierFilter.java
│   └── PiiRedactorFilter.java
└── service/
    ├── LlmService.java               Ollama WebClient wrapper
    ├── QueryClassifierService.java   classifies prompt into a category
    ├── ModelRoutingService.java      maps category → model name
    ├── QueryCategory.java            enum of supported categories
    ├── JailbreakDetectionService.java
    ├── PiiDetectionService.java
    └── TokenUsageService.java        tier-based Redis rate limiter
```
