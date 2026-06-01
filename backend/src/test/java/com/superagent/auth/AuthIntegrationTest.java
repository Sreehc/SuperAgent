package com.superagent.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanRefreshTokens() {
        jdbcTemplate.execute("DELETE FROM refresh_token");
    }

    @Test
    void shouldLoginAndReturnDefaultTenant() throws Exception {
        JsonNode json = login("admin", "password123");
        assertThat(json.path("success").asBoolean()).isTrue();
        assertThat(json.path("code").asText()).isEqualTo("OK");
        assertThat(json.path("data").path("accessToken").asText()).isNotBlank();
        assertThat(json.path("data").path("refreshToken").asText()).startsWith("rt_");
        assertThat(json.path("data").path("defaultTenant").path("role").asText()).isEqualTo("OWNER");
    }

    @Test
    void shouldRefreshRotatingToken() throws Exception {
        JsonNode login = login("admin", "password123");

        MvcResult response = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", login.path("data").path("refreshToken").asText()
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(response.getResponse().getContentAsString());
        assertThat(json.path("data").path("accessToken").asText()).isNotBlank();
        assertThat(json.path("data").path("refreshToken").asText()).isNotEqualTo(login.path("data").path("refreshToken").asText());
    }

    @Test
    void shouldLogoutAndRejectRevokedRefreshToken() throws Exception {
        JsonNode login = login("admin", "password123");
        String accessToken = login.path("data").path("accessToken").asText();
        String refreshToken = login.path("data").path("refreshToken").asText();

        MvcResult logoutResponse = mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult refreshResponse = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(objectMapper.readTree(logoutResponse.getResponse().getContentAsString()).path("data").path("revoked").asBoolean()).isTrue();
        assertThat(objectMapper.readTree(refreshResponse.getResponse().getContentAsString()).path("code").asText()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void shouldReturnCurrentUserAndTenants() throws Exception {
        JsonNode login = login("admin", "password123");
        String accessToken = login.path("data").path("accessToken").asText();

        MvcResult response = mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(response.getResponse().getContentAsString());
        assertThat(json.path("data").path("username").asText()).isEqualTo("admin");
        assertThat(json.path("data").path("tenants").size()).isEqualTo(1);
    }

    @Test
    void shouldForbidMemberFromListingTenantMembers() throws Exception {
        JsonNode login = login("member", "password123");
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();

        MvcResult response = mockMvc.perform(get("/api/v1/tenants/{tenantId}/members", tenantId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.path("data").path("accessToken").asText())
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(objectMapper.readTree(response.getResponse().getContentAsString()).path("code").asText()).isEqualTo("FORBIDDEN");
    }

    @Test
    void shouldAllowOwnerToListTenantMembers() throws Exception {
        JsonNode login = login("admin", "password123");
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();

        MvcResult response = mockMvc.perform(get("/api/v1/tenants/{tenantId}/members", tenantId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.path("data").path("accessToken").asText())
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(response.getResponse().getContentAsString());
        assertThat(json.path("data").size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldRejectUnauthenticatedMeRequest() throws Exception {
        MvcResult response = mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(objectMapper.readTree(response.getResponse().getContentAsString()).path("code").asText()).isEqualTo("UNAUTHORIZED");
    }

    private JsonNode login(String username, String password) throws Exception {
        MvcResult response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(response.getResponse().getContentAsString());
    }
}
