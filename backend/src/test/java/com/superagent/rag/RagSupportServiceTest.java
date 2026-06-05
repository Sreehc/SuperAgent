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
        RagSupportService service = new RagSupportService(new SuperAgentProperties(), null);

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

        RagSupportService.BudgetedEvidenceResult budgeted = service.applyThresholdAndBudget("A1", fused, 0.35d, 2, 2, 1000);
        assertThat(budgeted.evidences()).singleElement().extracting(RagEvidence::chunkId).isEqualTo(100L);
        assertThat(budgeted.evidences().getFirst().metadata()).containsKey("channels");
        assertThat(budgeted.evidences().getFirst().metadata()).containsEntry("lexicalMatched", true);
        assertThat(budgeted.belowThresholdFilteredCount()).isEqualTo(2);
    }

    @Test
    void shouldFilterOutIrrelevantEvidenceAndApplyTotalBudget() {
        RagSupportService service = new RagSupportService(new SuperAgentProperties(), null);

        List<RagEvidence> evidences = List.of(
                new RagEvidence("hybrid", 1L, 10L, 100L, "文档A", 1, "退款规则：7日内申请", null, 0.9d, Map.of("channels", List.of("vector", "keyword"))),
                new RagEvidence("vector", 1L, 11L, 101L, "文档B", 1, "部署日志路径说明", null, 0.8d, Map.of("channels", List.of("vector"))),
                new RagEvidence("hybrid", 1L, 12L, 102L, "文档C", 1, "退款申请需要订单截图", null, 0.7d, Map.of("channels", List.of("vector", "keyword")))
        );

        RagSupportService.BudgetedEvidenceResult filtered = service.applyThresholdAndBudget("退款 规则 七日", evidences, 0.35d, 3, 3, 1000);
        assertThat(filtered.evidences()).extracting(RagEvidence::chunkId).containsExactly(100L, 102L);
        assertThat(filtered.belowThresholdFilteredCount()).isEqualTo(1);

        RagSupportService.BudgetedEvidenceResult totalBudgeted = service.applyTotalBudget(filtered.evidences(), 1, 3, 1000);
        assertThat(totalBudgeted.evidences()).singleElement().extracting(RagEvidence::chunkId).isEqualTo(100L);
        assertThat(totalBudgeted.evidenceLimitTrimmedCount()).isEqualTo(1);
    }

    @Test
    void shouldLimitEvidencePerDocument() {
        RagSupportService service = new RagSupportService(new SuperAgentProperties(), null);

        List<RagEvidence> evidences = List.of(
                new RagEvidence("hybrid", 1L, 10L, 100L, "文档A", 1, "退款规则：7日内申请", null, 0.9d, Map.of("channels", List.of("vector", "keyword"))),
                new RagEvidence("hybrid", 1L, 10L, 101L, "文档A", 2, "退款申请需要订单截图", null, 0.8d, Map.of("channels", List.of("vector", "keyword"))),
                new RagEvidence("hybrid", 1L, 10L, 102L, "文档A", 3, "退款到账时效说明", null, 0.7d, Map.of("channels", List.of("vector", "keyword"))),
                new RagEvidence("hybrid", 1L, 11L, 103L, "文档B", 1, "售后工单入口", null, 0.6d, Map.of("channels", List.of("vector", "keyword")))
        );

        RagSupportService.BudgetedEvidenceResult filtered = service.applyThresholdAndBudget("退款 规则", evidences, 0.35d, 4, 2, 1000);

        assertThat(filtered.evidences()).extracting(RagEvidence::chunkId).containsExactly(100L, 101L, 103L);
        assertThat(filtered.diversityLimited()).isTrue();
        assertThat(filtered.perDocumentTrimmedCount()).isEqualTo(1);
    }

    @Test
    void shouldApplyEvidenceCharacterBudget() {
        RagSupportService service = new RagSupportService(new SuperAgentProperties(), null);

        List<RagEvidence> evidences = List.of(
                new RagEvidence("hybrid", 1L, 10L, 100L, "文档A", 1, "退款规则：7日内申请", null, 0.9d, Map.of("channels", List.of("vector", "keyword"))),
                new RagEvidence("hybrid", 1L, 11L, 101L, "文档B", 1, "退款申请需要订单截图和售后工单编号", null, 0.8d, Map.of("channels", List.of("vector", "keyword")))
        );

        RagSupportService.BudgetedEvidenceResult perQuestionBudgeted = service.applyThresholdAndBudget("退款 规则", evidences, 0.35d, 2, 2, 12);
        assertThat(perQuestionBudgeted.evidences()).singleElement().extracting(RagEvidence::chunkId).isEqualTo(100L);
        assertThat(perQuestionBudgeted.charBudgetTrimmedCount()).isEqualTo(1);

        RagSupportService.BudgetedEvidenceResult totalBudgeted = service.applyTotalBudget(evidences, 2, 2, 12);
        assertThat(totalBudgeted.evidences()).singleElement().extracting(RagEvidence::chunkId).isEqualTo(100L);
        assertThat(totalBudgeted.charBudgetTrimmedCount()).isEqualTo(1);
    }
}
