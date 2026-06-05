package com.superagent.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.superagent.chat.service.ChatModelClient;
import com.superagent.rag.domain.RagAnswer;
import com.superagent.rag.domain.RagEvidence;
import com.superagent.rag.service.RagChatComposer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RagChatComposerTest {

    @Test
    void shouldAppendCitationWhenForceCitationEnabledAndModelOmitsIt() {
        ChatModelClient chatModelClient = request -> new ChatModelClient.ModelResponse(
                "这是基于证据整理的回答。",
                List.of("这是基于证据整理的回答。"),
                List.of(),
                "test-provider",
                "test-model",
                10,
                12,
                "stop"
        );
        RagChatComposer composer = new RagChatComposer(chatModelClient);

        RagAnswer answer = composer.answer(
                "退款规则是什么？",
                "退款规则是什么？",
                1L,
                "当前问题: 退款规则是什么？",
                List.of(new RagEvidence(
                        "hybrid",
                        1L,
                        10L,
                        100L,
                        "退款规则文档",
                        1,
                        "退款需在7日内申请",
                        "退款规则",
                        0.92d,
                        Map.of()
                )),
                true
        );

        assertThat(answer.fullText()).contains("参考依据：[1]");
        assertThat(answer.citationAppended()).isTrue();
    }

    @Test
    void shouldKeepOriginalAnswerWhenCitationAlreadyPresent() {
        ChatModelClient chatModelClient = request -> new ChatModelClient.ModelResponse(
                "这是回答。[1]",
                List.of("这是回答。[1]"),
                List.of(),
                "test-provider",
                "test-model",
                10,
                12,
                "stop"
        );
        RagChatComposer composer = new RagChatComposer(chatModelClient);

        RagAnswer answer = composer.answer(
                "退款规则是什么？",
                "退款规则是什么？",
                1L,
                "当前问题: 退款规则是什么？",
                List.of(new RagEvidence(
                        "hybrid",
                        1L,
                        10L,
                        100L,
                        "退款规则文档",
                        1,
                        "退款需在7日内申请",
                        "退款规则",
                        0.92d,
                        Map.of()
                )),
                true
        );

        assertThat(answer.fullText()).isEqualTo("这是回答。[1]");
        assertThat(answer.citationAppended()).isFalse();
    }

    @Test
    void shouldNormalizeInvalidCitationToAvailableEvidenceOnly() {
        ChatModelClient chatModelClient = request -> new ChatModelClient.ModelResponse(
                "这是回答。[4][1]",
                List.of("这是回答。[4][1]"),
                List.of(),
                "test-provider",
                "test-model",
                10,
                12,
                "stop"
        );
        RagChatComposer composer = new RagChatComposer(chatModelClient);

        RagAnswer answer = composer.answer(
                "退款规则是什么？",
                "退款规则是什么？",
                1L,
                "当前问题: 退款规则是什么？",
                List.of(
                        new RagEvidence("hybrid", 1L, 10L, 100L, "退款规则文档", 1, "退款需在7日内申请", "退款规则", 0.92d, Map.of()),
                        new RagEvidence("hybrid", 1L, 10L, 101L, "退款规则文档", 2, "退款申请需要订单截图", "退款材料", 0.88d, Map.of())
                ),
                true
        );

        assertThat(answer.fullText()).isEqualTo("这是回答。\n\n参考依据：[1]");
        assertThat(answer.citationAppended()).isTrue();
    }

    @Test
    void shouldAppendDefaultCitationWhenOnlyInvalidCitationExists() {
        ChatModelClient chatModelClient = request -> new ChatModelClient.ModelResponse(
                "这是回答。[9]",
                List.of("这是回答。[9]"),
                List.of(),
                "test-provider",
                "test-model",
                10,
                12,
                "stop"
        );
        RagChatComposer composer = new RagChatComposer(chatModelClient);

        RagAnswer answer = composer.answer(
                "退款规则是什么？",
                "退款规则是什么？",
                1L,
                "当前问题: 退款规则是什么？",
                List.of(new RagEvidence(
                        "hybrid",
                        1L,
                        10L,
                        100L,
                        "退款规则文档",
                        1,
                        "退款需在7日内申请",
                        "退款规则",
                        0.92d,
                        Map.of()
                )),
                true
        );

        assertThat(answer.fullText()).isEqualTo("这是回答。\n\n参考依据：[1]");
        assertThat(answer.citationAppended()).isTrue();
    }
}
