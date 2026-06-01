package com.superagent.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.superagent.infra.config.SuperAgentProperties;
import com.superagent.rag.domain.RagEvidence;
import com.superagent.rag.domain.RetrievalResult;
import com.superagent.rag.service.RagSupportService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RagSupportServiceTest {

    @Test
    void shouldFuseWithRrfAndApplyBudget() {
        RagSupportService service = new RagSupportService(new SuperAgentProperties());

        List<RetrievalResult> vectorResults = List.of(
                new RetrievalResult("vector", 1L, 10L, 100L, "文档A", 1, "chunk A1", null, 0.8d, Map.of()),
                new RetrievalResult("vector", 1L, 11L, 101L, "文档B", 1, "chunk B1", null, 0.7d, Map.of())
        );
        List<RetrievalResult> keywordResults = List.of(
                new RetrievalResult("keyword", 1L, 10L, 100L, "文档A", 1, "chunk A1", null, 0.9d, Map.of()),
                new RetrievalResult("keyword", 1L, 12L, 102L, "文档C", 1, "chunk A2", null, 0.6d, Map.of())
        );

        List<RagEvidence> fused = service.fuseWithRrf(vectorResults, keywordResults, 60);
        assertThat(fused.getFirst().chunkId()).isEqualTo(100L);
        assertThat(fused.getFirst().score()).isBetween(0.9d, 1.0d);

        List<RagEvidence> budgeted = service.applyThresholdAndBudget("A1", fused, 0.35d, 2);
        assertThat(budgeted).singleElement().extracting(RagEvidence::chunkId).isEqualTo(100L);
        assertThat(budgeted.getFirst().metadata()).containsKey("channels");
        assertThat(budgeted.getFirst().metadata()).containsEntry("lexicalMatched", true);
    }

    @Test
    void shouldFilterOutIrrelevantEvidenceAndApplyTotalBudget() {
        RagSupportService service = new RagSupportService(new SuperAgentProperties());

        List<RagEvidence> evidences = List.of(
                new RagEvidence("hybrid", 1L, 10L, 100L, "文档A", 1, "退款规则：7日内申请", null, 0.9d, Map.of("channels", List.of("vector", "keyword"))),
                new RagEvidence("vector", 1L, 11L, 101L, "文档B", 1, "部署日志路径说明", null, 0.8d, Map.of("channels", List.of("vector"))),
                new RagEvidence("hybrid", 1L, 12L, 102L, "文档C", 1, "退款申请需要订单截图", null, 0.7d, Map.of("channels", List.of("vector", "keyword")))
        );

        List<RagEvidence> filtered = service.applyThresholdAndBudget("退款 规则 七日", evidences, 0.35d, 3);
        assertThat(filtered).extracting(RagEvidence::chunkId).containsExactly(100L, 102L);

        List<RagEvidence> totalBudgeted = service.applyTotalBudget(filtered, 1);
        assertThat(totalBudgeted).singleElement().extracting(RagEvidence::chunkId).isEqualTo(100L);
    }
}
