package com.superagent.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.infra.config.SuperAgentProperties;
import com.superagent.rag.service.QueryUnderstandingService;
import com.superagent.rag.service.RagSupportService;
import com.superagent.settings.domain.ModelSettings;
import com.superagent.settings.service.RuntimeSettingsService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class QueryUnderstandingServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldFallbackToRulePathWhenProviderIsUnavailable() {
        SuperAgentProperties properties = new SuperAgentProperties();
        properties.getAi().setChatProvider("local-fake");
        RuntimeSettingsService runtimeSettingsService = Mockito.mock(RuntimeSettingsService.class);
        QueryUnderstandingService service = new QueryUnderstandingService(properties, runtimeSettingsService, new ObjectMapper());
        RagSupportService supportService = new RagSupportService(properties, null);
        RagSupportService.EffectiveRagSettings settings = new RagSupportService.EffectiveRagSettings(
                true,
                true,
                true,
                true,
                true,
                true,
                20,
                20,
                20,
                60,
                false,
                1,
                3,
                8,
                0.35d,
                4
        );

        QueryUnderstandingService.QueryUnderstandingResult result = service.understand(
                "退款规则是什么？",
                List.of("上一个问题是售后范围", "请继续"),
                settings,
                supportService
        );

        assertThat(result.source()).isEqualTo("provider_unavailable");
        assertThat(result.answerMode()).isEqualTo("single_question");
        assertThat(result.rewrittenQuestion()).contains("结合上下文");
        assertThat(result.subQuestions()).singleElement().satisfies(item -> assertThat(item).contains("退款规则是什么"));
    }

    @Test
    void shouldUseModelStructuredPayloadWhenProviderReturnsJson() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            String contentJson = "{\"rewrittenQuestion\":\"退款规则和申请材料分别是什么？\",\"subQuestions\":[\"退款规则是什么？\",\"申请材料需要什么？\"],\"answerMode\":\"decomposed_multi_question\",\"confidence\":0.91}";
            String responseJson = """
                    {
                      "id": "chatcmpl_query_understanding",
                      "model": "gpt-4.1-mini",
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "content": %s
                          }
                        }
                      ]
                    }
                    """.formatted(new ObjectMapper().writeValueAsString(contentJson));
            byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();

        SuperAgentProperties properties = new SuperAgentProperties();
        properties.getAi().setChatProvider("openai-compatible");
        RuntimeSettingsService runtimeSettingsService = Mockito.mock(RuntimeSettingsService.class);
        Mockito.when(runtimeSettingsService.resolveModelSettings(10001L)).thenReturn(new ModelSettings(
                "openai-compatible",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "gpt-4.1-mini",
                "text-embedding-3-small",
                "sk-test"
        ));
        TenantContextHolder.set(new TenantContext(10001L, TenantRole.OWNER));

        QueryUnderstandingService service = new QueryUnderstandingService(properties, runtimeSettingsService, new ObjectMapper());
        RagSupportService supportService = new RagSupportService(properties, null);
        RagSupportService.EffectiveRagSettings settings = new RagSupportService.EffectiveRagSettings(
                true,
                true,
                true,
                true,
                true,
                true,
                20,
                20,
                20,
                60,
                false,
                1,
                3,
                8,
                0.35d,
                4
        );

        QueryUnderstandingService.QueryUnderstandingResult result = service.understand(
                "退款规则和申请材料是什么？",
                List.of("请按知识库回答"),
                settings,
                supportService
        );

        assertThat(result.source()).isEqualTo("model");
        assertThat(result.answerMode()).isEqualTo("decomposed_multi_question");
        assertThat(result.confidence()).isEqualTo(0.91d);
        assertThat(result.rewrittenQuestion()).isEqualTo("退款规则和申请材料分别是什么？");
        assertThat(result.subQuestions()).containsExactly("退款规则是什么？", "申请材料需要什么？");
    }
}
