package com.securellm.auth;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OidcDiscoveryTest {

    @Autowired MockMvc mockMvc;

    @Nested
    class OpenIdConfiguration {

        @Test
        void discoveryEndpoint_isPubliclyAccessible() throws Exception {
            mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk());
        }

        @Test
        void discoveryEndpoint_containsIssuer() throws Exception {
            mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(jsonPath("$.issuer").value("http://localhost:9000"));
        }

        @Test
        void discoveryEndpoint_containsRequiredEndpoints() throws Exception {
            mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(jsonPath("$.token_endpoint").isNotEmpty())
                .andExpect(jsonPath("$.jwks_uri").isNotEmpty())
                .andExpect(jsonPath("$.authorization_endpoint").isNotEmpty());
        }

        @Test
        void discoveryEndpoint_advertisesSupportedGrantTypes() throws Exception {
            mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(jsonPath("$.grant_types_supported").isArray());
        }
    }

    @Nested
    class JwksEndpoint {

        @Test
        void jwksEndpoint_isPubliclyAccessible() throws Exception {
            mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk());
        }

        @Test
        void jwksEndpoint_returnsRsaKey() throws Exception {
            mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].e").isNotEmpty());
        }

        @Test
        void jwksEndpoint_doesNotExposePrivateKey() throws Exception {
            mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andExpect(jsonPath("$.keys[0].p").doesNotExist())
                .andExpect(jsonPath("$.keys[0].q").doesNotExist());
        }
    }
}
