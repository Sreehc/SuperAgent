package com.superagent.rag.web;

import com.superagent.common.api.ApiResponse;
import com.superagent.knowledge.web.KnowledgeController;
import com.superagent.rag.domain.RetrievalResult;
import com.superagent.rag.service.RetrievalService;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("${super-agent.app.api-base-path}/retrievals")
public class RetrievalController {

    private final RetrievalService retrievalService;

    public RetrievalController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @GetMapping
    public ApiResponse<KnowledgeController.PagedResponse<RetrievalItem>> search(
            @RequestParam @Size(max = 1000) String query,
            @RequestParam(required = false) Long knowledgeBaseId,
            @RequestParam(required = false) Integer topK
    ) {
        List<RetrievalResult> results = retrievalService.search(query, knowledgeBaseId, topK);
        return ApiResponse.success(new KnowledgeController.PagedResponse<>(
                results.stream().map(this::toItem).toList(),
                1,
                results.size(),
                results.size()
        ));
    }

    private RetrievalItem toItem(RetrievalResult result) {
        return new RetrievalItem(
                result.channel(),
                result.knowledgeBaseId(),
                result.documentId(),
                result.chunkId(),
                result.documentTitle(),
                result.chunkNo(),
                result.content(),
                result.sectionTitle(),
                result.score(),
                result.metadata()
        );
    }

    public record RetrievalItem(
            String channel,
            long knowledgeBaseId,
            long documentId,
            long chunkId,
            String documentTitle,
            int chunkNo,
            String content,
            String sectionTitle,
            double score,
            Map<String, Object> metadata
    ) {
    }
}
