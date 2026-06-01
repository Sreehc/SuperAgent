package com.superagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SuperAgentApplicationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @LocalServerPort
    private int port;

    @Test
    void shouldStartApplicationAndExposeHealth() {
        String healthResponse = testRestTemplate.getForObject("http://localhost:" + port + "/actuator/health", String.class);

        assertThat(healthResponse).contains("\"status\":\"UP\"");
    }

    @Test
    void shouldApplyDatabaseMigrations() {
        List<String> extensions = jdbcTemplate.queryForList(
                "SELECT extname FROM pg_extension WHERE extname IN ('vector', 'pg_trgm') ORDER BY extname",
                String.class
        );
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('tenant', 'user_account', 'knowledge_document', 'conversation_exchange', 'audit_log', 'tenant_runtime_setting') ORDER BY table_name",
                String.class
        );

        assertThat(extensions).containsExactly("pg_trgm", "vector");
        assertThat(tables).containsExactly("audit_log", "conversation_exchange", "knowledge_document", "tenant", "tenant_runtime_setting", "user_account");
    }
}
