package com.securellm.auth;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityFilterChainTest {

    @Autowired MockMvc mockMvc;

    @Nested
    class PublicEndpoints {

        @Test
        void actuatorHealth_noAuthRequired() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        }

        @Test
        void actuatorInfo_noAuthRequired() throws Exception {
            mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
        }

        @Test
        void oidcDiscovery_noAuthRequired() throws Exception {
            mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk());
        }

        @Test
        void jwks_noAuthRequired() throws Exception {
            mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk());
        }
    }

    @Nested
    class ProtectedEndpoints {

        @Test
        void unauthenticatedRequest_redirectsToLogin() throws Exception {
            mockMvc.perform(get("/some-protected-path"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        }

        @Test
        void authenticatedRequest_isAllowed() throws Exception {
            mockMvc.perform(get("/actuator/health")
                    .with(user("user").roles("USER")))
                .andExpect(status().isOk());
        }
    }

    @Nested
    class AuthorizationEndpoints {

        @Test
        void tokenEndpoint_withoutCredentials_returns401() throws Exception {
            mockMvc.perform(get("/oauth2/token"))
                .andExpect(status().is3xxRedirection());
        }

        @Test
        void authorizeEndpoint_withoutSession_doesNotReturn200() throws Exception {
            // Spring Auth Server validates request params before the auth redirect, so
            // the exact status is 302 (login redirect) or 4xx (param error) — never 200.
            mockMvc.perform(get("/oauth2/authorize")
                    .param("response_type", "code")
                    .param("client_id", "ai-gateway-client")
                    .param("redirect_uri", "http://localhost:8081/login/oauth2/code/ai-gateway-client")
                    .param("scope", "openid"))
                .andExpect(result ->
                    assertThat(result.getResponse().getStatus()).isNotEqualTo(200));
        }
    }
}
