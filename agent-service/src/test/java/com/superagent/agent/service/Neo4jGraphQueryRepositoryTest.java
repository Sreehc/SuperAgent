package com.superagent.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.superagent.agent.config.AgentServiceProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Values;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class Neo4jGraphQueryRepositoryTest {

    @Mock
    private Environment environment;

    @Mock
    private Driver driver;

    @Mock
    private Session session;

    @Mock
    private TransactionContext transactionContext;

    @Test
    void shouldReturnShortestPathPayloadForPathQuery() {
        AgentServiceProperties properties = new AgentServiceProperties();
        properties.getGraph().setEnabled(true);
        Neo4jGraphQueryRepository repository = new Neo4jGraphQueryRepository(properties, environment);
        ReflectionTestUtils.setField(repository, "driver", driver);

        when(environment.getActiveProfiles()).thenReturn(new String[0]);
        when(driver.session()).thenReturn(session);
        when(session.executeRead(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = (TransactionCallback<Object>) invocation.getArgument(0);
            return callback.execute(transactionContext);
        });

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        Result result = mock(Result.class);
        Record pathRecord = pathRecord();
        when(transactionContext.run(queryCaptor.capture(), anyMap())).thenReturn(result);
        when(result.list()).thenReturn(List.of(pathRecord));

        GraphQueryRequest request = new GraphQueryRequest(
                List.of("订单", "客户"),
                List.of("订单", "客户"),
                "订单",
                "客户",
                true,
                3
        );

        var payload = repository.query(10001L, request, 201L, 301L, 5);

        assertThat(payload).isPresent();
        assertThat(payload.orElseThrow().evidence()).hasSize(1);
        assertThat(payload.orElseThrow().evidence().get(0).get("queryMode")).isEqualTo("path");
        assertThat(payload.orElseThrow().evidence().get(0).get("path")).isEqualTo("订单 -> Chunk 1 -> 客户");
        assertThat(payload.orElseThrow().nodes()).hasSize(3);
        assertThat(payload.orElseThrow().edges()).hasSize(2);
        assertThat(queryCaptor.getValue()).contains("shortestPath");
        assertThat(queryCaptor.getValue()).contains("*..3");
    }

    private Record pathRecord() {
        Record record = mock(Record.class);
        when(record.get("knowledgeBaseId")).thenReturn(Values.value(201L));
        when(record.get("documentId")).thenReturn(Values.value(301L));
        when(record.get("documentTitle")).thenReturn(Values.value("退款指南"));
        when(record.get("chunkId")).thenReturn(Values.value(901L));
        when(record.get("chunkNo")).thenReturn(Values.value(1));
        when(record.get("content")).thenReturn(Values.value("订单和客户的关系在这一段中描述。"));
        when(record.get("entityText")).thenReturn(Values.value("订单"));
        when(record.get("relatedEntityText")).thenReturn(Values.value("客户"));
        when(record.get("pathNodes")).thenReturn(Values.value(List.of(
                Map.of("id", "entity:order", "type", "Entity", "label", "订单", "props", Map.of("tenantId", 10001L)),
                Map.of("id", "chunk:901", "type", "Chunk", "label", "Chunk 1", "props", Map.of("chunkNo", 1, "contentPreview", "订单和客户的关系在这一段中描述。")),
                Map.of("id", "entity:customer", "type", "Entity", "label", "客户", "props", Map.of("tenantId", 10001L))
        )));
        when(record.get("pathEdges")).thenReturn(Values.value(List.of(
                Map.of("sourceId", "entity:order", "targetId", "chunk:901", "type", "MENTIONS", "props", Map.of()),
                Map.of("sourceId", "chunk:901", "targetId", "entity:customer", "type", "MENTIONS", "props", Map.of())
        )));
        when(record.get("mentionEdge")).thenReturn(Values.NULL);
        when(record.get("entityNode")).thenReturn(Values.NULL);
        when(record.get("relatedEdge")).thenReturn(Values.NULL);
        when(record.get("relatedEntityNode")).thenReturn(Values.NULL);
        when(record.get("hopCount")).thenReturn(Values.value(2));
        when(record.get("pathLabels")).thenReturn(Values.value(List.of("订单", "Chunk 1", "客户")));
        when(record.get("queryMode")).thenReturn(Values.value("path"));
        return record;
    }
}
