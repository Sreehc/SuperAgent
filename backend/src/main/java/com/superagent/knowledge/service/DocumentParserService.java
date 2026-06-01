package com.superagent.knowledge.service;

import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import java.io.InputStream;
import java.util.Set;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DocumentParserService {

    private static final Set<String> SUPPORTED_TYPES = Set.of("pdf", "doc", "docx", "ppt", "pptx", "md", "html", "txt");

    private final Tika tika = new Tika();

    public ParsedDocument parse(String fileType, InputStream inputStream) {
        if (!SUPPORTED_TYPES.contains(fileType)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Unsupported document type: " + fileType);
        }
        try {
            String text = tika.parseToString(inputStream);
            String normalized = normalize(text);
            if (normalized.isBlank()) {
                throw new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Document content is empty after parsing");
            }
            return new ParsedDocument(normalized, normalized.length());
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse document content");
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    public record ParsedDocument(String content, int charCount) {
    }
}
