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
import org.springframework.mock.web.MockCookie;
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
    void cleanTestState() {
        jdbcTemplate.execute("DELETE FROM refresh_token");
        jdbcTemplate.execute("""
                DELETE FROM tenant_member
                WHERE user_id IN (
                    SELECT id FROM user_account WHERE username IN ('created-member', 'reset-member', 'blocked-member')
                )
                """);
        jdbcTemplate.execute("DELETE FROM user_account WHERE username IN ('created-member', 'reset-member', 'blocked-member')");
    }

    @Test
    void shouldLoginAndReturnDefaultTenant() throws Exception {
        JsonNode json = login("admin", "password123");
        assertThat(json.path("success").asBoolean()).isTrue();
        assertThat(json.path("code").asText()).isEqualTo("OK");
        assertThat(json.path("data").path("accessToken").asText()).isNotBlank();
        assertThat(json.path("data").has("refreshToken")).isFalse();
        assertThat(json.path("data").path("defaultTenant").path("role").asText()).isEqualTo("OWNER");
    }

    @Test
    void shouldRefreshRotatingToken() throws Exception {
        MvcResult loginResponse = loginResponse("admin", "password123");
        String refreshCookie = loginResponse.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(refreshCookie).contains("superagent.refreshToken=").contains("HttpOnly");
        MockCookie cookie = MockCookie.parse(refreshCookie);

        MvcResult response = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(response.getResponse().getContentAsString());
        assertThat(json.path("data").path("accessToken").asText()).isNotBlank();
        assertThat(json.path("data").has("refreshToken")).isFalse();
        assertThat(response.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("superagent.refreshToken=").contains("HttpOnly");
    }

    @Test
    void shouldLogoutAndRejectRevokedRefreshToken() throws Exception {
        MvcResult loginResponse = loginResponse("admin", "password123");
        JsonNode login = objectMapper.readTree(loginResponse.getResponse().getContentAsString());
        String accessToken = login.path("data").path("accessToken").asText();
        MockCookie cookie = MockCookie.parse(loginResponse.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        MvcResult logoutResponse = mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult refreshResponse = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(objectMapper.readTree(logoutResponse.getResponse().getContentAsString()).path("data").path("revoked").asBoolean()).isTrue();
        assertThat(logoutResponse.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
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
    void shouldAllowOwnerToCreateTenantMemberAndLogin() throws Exception {
        JsonNode owner = login("admin", "password123");
        long tenantId = owner.path("data").path("defaultTenant").path("id").asLong();

        MvcResult response = mockMvc.perform(post("/api/v1/tenants/{tenantId}/members", tenantId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.path("data").path("accessToken").asText())
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "created-member",
                                "displayName", "Created Member",
                                "email", "created-member@example.com",
                                "password", "test1234",
                                "role", "MEMBER"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode created = objectMapper.readTree(response.getResponse().getContentAsString());
        assertThat(created.path("data").path("username").asText()).isEqualTo("created-member");
        assertThat(created.path("data").path("role").asText()).isEqualTo("MEMBER");

        JsonNode createdLogin = login("created-member", "test1234");
        assertThat(createdLogin.path("data").path("defaultTenant").path("id").asLong()).isEqualTo(tenantId);
    }

    @Test
    void shouldAllowOwnerToResetTenantMemberPassword() throws Exception {
        JsonNode owner = login("admin", "password123");
        long tenantId = owner.path("data").path("defaultTenant").path("id").asLong();
        MvcResult createResponse = mockMvc.perform(post("/api/v1/tenants/{tenantId}/members", tenantId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.path("data").path("accessToken").asText())
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "reset-member",
                                "password", "test1234",
                                "role", "MEMBER"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        long memberUserId = objectMapper.readTree(createResponse.getResponse().getContentAsString()).path("data").path("userId").asLong();

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/members/{userId}/password", tenantId, memberUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.path("data").path("accessToken").asText())
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "newpass123"))))
                .andExpect(status().isOk());

        JsonNode resetLogin = login("reset-member", "newpass123");
        assertThat(resetLogin.path("data").path("user").path("username").asText()).isEqualTo("reset-member");
    }

    @Test
    void shouldForbidMemberFromCreatingTenantMember() throws Exception {
        JsonNode member = login("member", "password123");
        long tenantId = member.path("data").path("defaultTenant").path("id").asLong();

        MvcResult response = mockMvc.perform(post("/api/v1/tenants/{tenantId}/members", tenantId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.path("data").path("accessToken").asText())
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "blocked-member",
                                "password", "test1234",
                                "role", "MEMBER"
                        ))))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(objectMapper.readTree(response.getResponse().getContentAsString()).path("code").asText()).isEqualTo("FORBIDDEN");
    }

    @Test
    void shouldRejectUnauthenticatedMeRequest() throws Exception {
        MvcResult response = mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(objectMapper.readTree(response.getResponse().getContentAsString()).path("code").asText()).isEqualTo("UNAUTHORIZED");
    }

    private JsonNode login(String username, String password) throws Exception {
        return objectMapper.readTree(loginResponse(username, password).getResponse().getContentAsString());
    }

    private MvcResult loginResponse(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
    }
}
