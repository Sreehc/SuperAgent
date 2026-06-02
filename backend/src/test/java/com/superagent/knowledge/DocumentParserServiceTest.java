package com.superagent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.superagent.common.exception.AppException;
import com.superagent.knowledge.service.DocumentParserService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextBox;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

class DocumentParserServiceTest {
    private final DocumentParserService parserService = new DocumentParserService();

    @Test
    void shouldParseSupportedFormats() throws Exception {
        assertContent("txt", "plain text refund guide", bytes("plain text refund guide"));
        assertContent("md", "# refund guide\nsupport markdown", bytes("# refund guide\nsupport markdown"));
        assertContent("html", "refund guide", bytes("<html><body><h1>refund guide</h1><p>support html</p></body></html>"));
        assertContent("doc", "legacy word sample document", readResource("/documents/sample.doc"));
        assertContent("docx", "docx sample document", createDocx());
        assertContent("ppt", "ppt sample slide", createPpt());
        assertContent("pptx", "pptx sample slide", createPptx());
        assertContent("pdf", "pdf sample guide", readResource("/documents/sample.pdf"));
    }

    @Test
    void shouldRejectUnsupportedFormatAndBrokenPayload() {
        assertThatThrownBy(() -> parserService.parse("xlsx", new ByteArrayInputStream(new byte[] {1, 2, 3})))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Unsupported document type");

        assertThatThrownBy(() -> parserService.parse("doc", new ByteArrayInputStream(new byte[] {0x00, 0x01, 0x02, 0x03, 0x04})))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("empty after parsing");
    }

    private void assertContent(String fileType, String expectedSnippet, byte[] content) {
        DocumentParserService.ParsedDocument parsedDocument =
                parserService.parse(fileType, new ByteArrayInputStream(content));
        assertThat(parsedDocument.content()).contains(expectedSnippet);
        assertThat(parsedDocument.charCount()).isGreaterThan(0);
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] readResource(String location) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(location)) {
            if (inputStream == null) {
                throw new IOException("Missing test resource: " + location);
            }
            return inputStream.readAllBytes();
        }
    }

    private byte[] createDocx() throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("docx sample document");
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createPpt() throws IOException {
        try (HSLFSlideShow slideShow = new HSLFSlideShow(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            HSLFSlide slide = slideShow.createSlide();
            HSLFTextBox textBox = slide.createTextBox();
            textBox.setText("ppt sample slide");
            slide.addShape(textBox);
            slideShow.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createPptx() throws IOException {
        try (XMLSlideShow slideShow = new XMLSlideShow(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XSLFSlide slide = slideShow.createSlide();
            XSLFTextBox textBox = slide.createTextBox();
            textBox.setText("pptx sample slide");
            slideShow.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
