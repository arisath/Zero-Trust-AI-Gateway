# Auth Service

OAuth2 Authorization Server for the Zero-Trust AI Gateway. Authenticates users against OpenLDAP, issues signed JWT access tokens that the gateway validates on every request when running in `prod` profile.

Built on [Spring Authorization Server](https://spring.io/projects/spring-authorization-server).

## How Authentication Works

User identity and tier come from OpenLDAP:

```
User logs in at /login
        │
        ▼
BindAuthenticator binds to LDAP as cn=<user>,ou=users
        │
        ▼
DefaultLdapAuthoritiesPopulator looks up group membership in ou=groups
  cn=free     → ROLE_FREE
  cn=premium  → ROLE_PREMIUM
        │
        ▼
OAuth2TokenCustomizer reads ROLE_PREMIUM → "tier": "premium"
                               ROLE_FREE  → "tier": "free"
        │
        ▼
JWT issued with claim: { "tier": "free" | "premium" }
```

The `tier` claim is consumed by the gateway to apply per-tier rate limits. Client credentials tokens (machine-to-machine) do not carry a tier claim.

## Endpoints

| Endpoint | Method | Auth required | Description |
|---|---|---|---|
| `/oauth2/token` | POST | Client credentials (Basic) | Issue an access token |
| `/oauth2/authorize` | GET | Session | Start authorization-code flow |
| `/.well-known/openid-configuration` | GET | None | OIDC discovery document |
| `/oauth2/jwks` | GET | None | Public keys for JWT verification |
| `/login` | GET | None | User login form |
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
2. User is prompted to log in at `/login` — credentials are validated against LDAP
3. Auth server redirects back to your `redirect_uri` with a `code`
4. Exchange the code for a token at `/oauth2/token`

The issued token will contain `"tier": "free"` or `"tier": "premium"` based on LDAP group membership.

## Seed Users

Two users are provisioned automatically via `ldap/bootstrap.ldif`:

| Username | Password | LDAP group | Tier |
|---|---|---|---|
| `alice` | `alice123` | `cn=free,ou=groups` | free |
| `bob` | `bob123` | `cn=premium,ou=groups` | premium |

Change passwords via `LDAP_USER_PASSWORDS` in docker-compose or through phpLDAPadmin.

## Configuration

| Variable | Default | Description |
|---|---|---|
| `AUTH_ISSUER_URI` | `http://localhost:9000` | Token issuer — must be reachable by the gateway for JWKS validation |
| `AUTH_CLIENT_ID` | `ai-gateway-client` | OAuth2 client ID |
| `AUTH_CLIENT_SECRET` | `secret` | OAuth2 client secret — **change in production** |
| `LDAP_URL` | `ldap://localhost:1389` | OpenLDAP server URL |
| `LDAP_BASE` | `dc=securellm,dc=com` | LDAP base DN |
| `LDAP_MANAGER_DN` | `cn=admin,dc=securellm,dc=com` | Manager DN for group lookups |
| `LDAP_MANAGER_PASSWORD` | `adminpassword` | Manager password — **change in production** |

## Token Details

| Property | Value |
|---|---|
| Algorithm | RS256 (2048-bit RSA, generated at startup) |
| Access token TTL | 1 hour |
| Refresh token TTL | 7 days |
| Key persistence | In-memory — tokens are invalidated on restart |

> **Production note:** The RSA key pair is generated fresh on each startup. For production, replace `generateRsaKey()` in `AuthorizationServerConfig` with a persistent keystore (PKCS12 / JKS / cloud KMS) so existing tokens remain valid across restarts and deployments.

## Storage

| Store | Class | Production replacement |
|---|---|---|
| Registered clients | `InMemoryRegisteredClientRepository` | `JdbcRegisteredClientRepository` |
| Users | OpenLDAP via `LdapAuthenticationProvider` | Already external — replace with your org's directory |
| Issued tokens | In-memory | `JdbcOAuth2AuthorizationService` |

## Running Locally

Prerequisites: OpenLDAP running (or use Docker Compose which starts it automatically).

```bash
cd auth-service
LDAP_URL=ldap://localhost:1389 mvn spring-boot:run
```

The server starts on port **9000**. Start the gateway with:

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

Tests cover token issuance, JWT claim validation, credential rejection, OIDC discovery, and endpoint access rules. The LDAP health indicator is disabled during tests — LDAP itself is not required to run the test suite.
