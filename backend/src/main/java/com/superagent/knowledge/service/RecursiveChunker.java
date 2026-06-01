package com.superagent.knowledge.service;

import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.infra.config.SuperAgentProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RecursiveChunker {

    private static final String[] SEPARATORS = {"\n\n", "\n", "。", "！", "？", ".", "!", "?", " ", ""};

    private final SuperAgentProperties properties;

    public RecursiveChunker(SuperAgentProperties properties) {
        this.properties = properties;
    }

    public List<ChunkCandidate> chunk(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        int chunkSize = properties.getDocumentProcessing().getChunkSize();
        int overlap = Math.min(properties.getDocumentProcessing().getChunkOverlap(), chunkSize / 2);
        List<String> pieces = splitRecursively(normalized, chunkSize, 0);
        List<ChunkCandidate> chunks = new ArrayList<>();
        String carry = "";
        int maxChunkCount = properties.getDocumentProcessing().getMaxChunkCount();
        for (String piece : pieces) {
            String candidate = (carry + piece).trim();
            if (candidate.isBlank()) {
                continue;
            }
            if (candidate.length() <= chunkSize) {
                chunks.add(new ChunkCandidate(chunks.size() + 1, candidate));
                carry = tail(candidate, overlap);
            } else {
                int start = 0;
                while (start < candidate.length()) {
                    int end = Math.min(start + chunkSize, candidate.length());
                    String sliced = candidate.substring(start, end).trim();
                    if (!sliced.isBlank()) {
                        chunks.add(new ChunkCandidate(chunks.size() + 1, sliced));
                    }
                    if (end >= candidate.length()) {
                        carry = tail(candidate.substring(Math.max(0, end - overlap), end), overlap);
                        break;
                    }
                    start = Math.max(end - overlap, start + 1);
                }
            }
            if (chunks.size() > maxChunkCount) {
                throw new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Chunk count exceeds configured limit");
            }
        }
        return chunks;
    }

    private List<String> splitRecursively(String content, int chunkSize, int level) {
        if (content.length() <= chunkSize || level >= SEPARATORS.length - 1) {
            return List.of(content);
        }
        String separator = SEPARATORS[level];
        if (separator.isEmpty()) {
            return splitByLength(content, chunkSize);
        }
        String[] parts = content.split(java.util.regex.Pattern.quote(separator));
        if (parts.length == 1) {
            return splitRecursively(content, chunkSize, level + 1);
        }
        List<String> merged = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            String candidate = current.isEmpty() ? part : current + separator + part;
            if (candidate.length() <= chunkSize) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (!current.isEmpty()) {
                merged.add(current.toString());
                current.setLength(0);
            }
            if (part.length() > chunkSize) {
                merged.addAll(splitRecursively(part, chunkSize, level + 1));
            } else {
                current.append(part);
            }
        }
        if (!current.isEmpty()) {
            merged.add(current.toString());
        }
        return merged;
    }

    private List<String> splitByLength(String content, int chunkSize) {
        List<String> segments = new ArrayList<>();
        for (int start = 0; start < content.length(); start += chunkSize) {
            segments.add(content.substring(start, Math.min(content.length(), start + chunkSize)));
        }
        return segments;
    }

    private String tail(String content, int overlap) {
        if (overlap <= 0 || content.length() <= overlap) {
            return content;
        }
        return content.substring(content.length() - overlap);
    }

    public record ChunkCandidate(int chunkNo, String content) {
    }
}
