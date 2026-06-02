package com.superagent.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class GraphQueryServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private Neo4jGraphQueryRepository neo4jGraphQueryRepository;

    @Test
    void shouldPreferNeo4jPayloadWhenAvailable() {
        GraphQueryService service = new GraphQueryService(jdbcTemplate, neo4jGraphQueryRepository);
        Neo4jGraphQueryRepository.GraphQueryPayload payload = new Neo4jGraphQueryRepository.GraphQueryPayload(
                List.of(Map.of("documentId", 301L, "chunkId", 901L, "content", "退款实体关系")),
                List.of(Map.of("id", "entity:301:refund", "type", "Entity", "label", "退款", "metadata", Map.of())),
                List.of(Map.of("sourceId", "chunk:901", "targetId", "entity:301:refund", "type", "MENTIONS", "metadata", Map.of()))
        );

        when(neo4jGraphQueryRepository.query(10001L, List.of("退款关系"), 201L, null, 5))
                .thenReturn(java.util.Optional.of(payload));

        Map<String, Object> result = service.query(10001L, "退款关系", 201L, null, 5);

        assertThat(result.get("source")).isEqualTo("neo4j");
        assertThat((List<?>) result.get("evidence")).hasSize(1);
        verify(jdbcTemplate, never()).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void shouldFallbackToPostgresWhenNeo4jGraphIsUnavailable() throws Exception {
        GraphQueryService service = new GraphQueryService(jdbcTemplate, neo4jGraphQueryRepository);
        when(neo4jGraphQueryRepository.query(10001L, List.of("refund", "graph"), 201L, 301L, 3))
                .thenReturn(java.util.Optional.empty());
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<Object> rowMapper = (RowMapper<Object>) invocation.getArgument(2);
                    ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
                    when(resultSet.getLong("knowledge_base_id")).thenReturn(201L);
                    when(resultSet.getLong("document_id")).thenReturn(301L);
                    when(resultSet.getString("document_title")).thenReturn("退款指南");
                    when(resultSet.getLong("chunk_id")).thenReturn(901L);
                    when(resultSet.getInt("chunk_no")).thenReturn(1);
                    when(resultSet.getString("content")).thenReturn("refund graph entity");
                    when(resultSet.getDouble("score")).thenReturn(2.0d);
                    return List.of(rowMapper.mapRow(resultSet, 0));
                });

        Map<String, Object> result = service.query(10001L, "refund graph", 201L, 301L, 3);

        assertThat(result.get("source")).isEqualTo("postgres_fallback");
        assertThat((List<?>) result.get("evidence")).hasSize(1);
        assertThat((List<?>) result.get("nodes")).isNotEmpty();
        assertThat((List<?>) result.get("edges")).isNotEmpty();
    }
}
