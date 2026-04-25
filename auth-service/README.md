# Auth Service

OAuth2 Authorization Server for the Zero-Trust AI Gateway. Issues signed JWT access tokens that the gateway validates on every request when running in `prod` profile.

Built on [Spring Authorization Server](https://spring.io/projects/spring-authorization-server).

## Endpoints

| Endpoint | Method | Auth required | Description |
|---|---|---|---|
| `/oauth2/token` | POST | Client credentials (Basic) | Issue an access token |
| `/oauth2/authorize` | GET | Session | Start authorization-code flow |
| `/.well-known/openid-configuration` | GET | None | OIDC discovery document |
| `/oauth2/jwks` | GET | None | Public keys for JWT verification |
| `/actuator/health` | GET | None | Health check |

## Supported Grant Types

### Client Credentials — service-to-service
No user involved. Authenticate with client ID and secret.

```bash
curl -X POST http://localhost:9000/oauth2/token \
  -u ai-gateway-client:secret \
  -d "grant_type=client_credentials&scope=gateway.read"
```

Response:
```json
{
  "access_token": "eyJ...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "gateway.read"
}
```

### Authorization Code + PKCE — browser / human user
1. Redirect the browser to `/oauth2/authorize` with `response_type=code`
2. User is prompted to log in via the form at `/login`
3. Auth server redirects back to your `redirect_uri` with a `code`
4. Exchange the code for a token at `/oauth2/token`

## Using the Token with the Gateway

Start the gateway with `SPRING_PROFILES_ACTIVE=prod`, then pass the token as a Bearer header:

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

## Configuration

All values have safe defaults for local development. Override via environment variables in production.

| Variable | Default | Description |
|---|---|---|
| `AUTH_ISSUER_URI` | `http://localhost:9000` | Token issuer — must be reachable by the gateway for JWKS validation |
| `AUTH_CLIENT_ID` | `ai-gateway-client` | OAuth2 client ID |
| `AUTH_CLIENT_SECRET` | `secret` | OAuth2 client secret — **change in production** |
| `AUTH_USER` | `user` | In-memory user for authorization-code / browser login |
| `AUTH_PASSWORD` | `password` | In-memory user password — **change in production** |

## Token Details

| Property | Value |
|---|---|
| Algorithm | RS256 (2048-bit RSA, generated at startup) |
| Access token TTL | 1 hour |
| Refresh token TTL | 7 days |
| Key persistence | In-memory — tokens are invalidated on restart |

> **Production note:** The RSA key pair is generated fresh on each startup. For production, replace `generateRsaKey()` in `AuthorizationServerConfig` with a persistent keystore (PKCS12 / JKS / cloud KMS) so existing tokens remain valid across restarts and deployments.

## Storage

Both the client registry and the user store are currently **in-memory**:

| Store | Class | Production replacement |
|---|---|---|
| Registered clients | `InMemoryRegisteredClientRepository` | `JdbcRegisteredClientRepository` |
| Users | `InMemoryUserDetailsManager` | JPA-backed `UserDetailsService` |
| Issued tokens | In-memory | `JdbcOAuth2AuthorizationService` |

## Running Locally

```bash
cd auth-service
mvn spring-boot:run
```

The server starts on port **9000**. The gateway (port 8081) must be started with:

```bash
SPRING_PROFILES_ACTIVE=prod \
JWT_ISSUER_URI=http://localhost:9000 \
JWT_JWKS_URI=http://localhost:9000/oauth2/jwks \
mvn spring-boot:run -pl ai-gateway
```

## Running Tests

```bash
mvn test -pl auth-service
```

23 tests covering:
- Token issuance and JWT claim validation
- Rejection of bad credentials, unknown clients, missing grant type
- OIDC discovery document and JWKS endpoint content
- Public vs protected endpoint access rules
