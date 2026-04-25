package com.securellm.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TokenEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Nested
    class ClientCredentialsGrant {

        @Test
        void validCredentials_returnsAccessToken() throws Exception {
            mockMvc.perform(post("/oauth2/token")
                    .with(httpBasic("ai-gateway-client", "secret"))
                    .param("grant_type", "client_credentials")
                    .param("scope", "gateway.read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").isNumber());
        }

        @Test
        void validCredentials_noScopeRequested_returnsToken() throws Exception {
            mockMvc.perform(post("/oauth2/token")
                    .with(httpBasic("ai-gateway-client", "secret"))
                    .param("grant_type", "client_credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty());
        }

        @Test
        void wrongClientSecret_returns401() throws Exception {
            mockMvc.perform(post("/oauth2/token")
                    .with(httpBasic("ai-gateway-client", "wrong-secret"))
                    .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void unknownClient_returns401() throws Exception {
            mockMvc.perform(post("/oauth2/token")
                    .with(httpBasic("unknown-client", "secret"))
                    .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void missingGrantType_returns400() throws Exception {
            mockMvc.perform(post("/oauth2/token")
                    .with(httpBasic("ai-gateway-client", "secret")))
                .andExpect(status().isBadRequest());
        }

        @Test
        void noCredentials_requestIsRejected() throws Exception {
            // Spring's form-login entry point redirects browser requests to /login (302).
            // Either way, no token is issued.
            mockMvc.perform(post("/oauth2/token")
                    .param("grant_type", "client_credentials"))
                .andExpect(status().is3xxRedirection());
        }
    }

    @Nested
    class IssuedTokenClaims {

        @Test
        void accessToken_isSignedJwtWithExpectedClaims() throws Exception {
            MvcResult result = mockMvc.perform(post("/oauth2/token")
                    .with(httpBasic("ai-gateway-client", "secret"))
                    .param("grant_type", "client_credentials")
                    .param("scope", "gateway.read"))
                .andExpect(status().isOk())
                .andReturn();

            String body = result.getResponse().getContentAsString();
            JsonNode tokenResponse = objectMapper.readTree(body);
            String jwt = tokenResponse.get("access_token").asText();

            // A JWT has three dot-separated parts
            String[] parts = jwt.split("\\.");
            assertThat(parts).hasSize(3);

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode claims = objectMapper.readTree(payloadJson);

            assertThat(claims.get("iss").asText()).isEqualTo("http://localhost:9000");
            assertThat(claims.get("sub").asText()).isEqualTo("ai-gateway-client");
            // scope is a JSON array in Spring Auth Server — toString() gives ["gateway.read"]
            assertThat(claims.get("scope").toString()).contains("gateway.read");
            assertThat(claims.has("exp")).isTrue();
            assertThat(claims.has("iat")).isTrue();
        }

        @Test
        void accessToken_expiryIsOneHourFromNow() throws Exception {
            long before = System.currentTimeMillis() / 1000;

            MvcResult result = mockMvc.perform(post("/oauth2/token")
                    .with(httpBasic("ai-gateway-client", "secret"))
                    .param("grant_type", "client_credentials"))
                .andExpect(status().isOk())
                .andReturn();

            long after = System.currentTimeMillis() / 1000;

            JsonNode tokenResponse = objectMapper.readTree(result.getResponse().getContentAsString());
            long expiresIn = tokenResponse.get("expires_in").asLong();

            // TokenSettings sets 1 hour — allow a few seconds of test execution slack
            assertThat(expiresIn).isBetween(3590L, 3600L);
        }
    }
}
