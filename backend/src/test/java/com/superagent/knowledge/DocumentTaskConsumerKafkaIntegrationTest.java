package com.superagent.knowledge;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.superagent.knowledge.messaging.DocumentTaskMessage;
import com.superagent.knowledge.service.DocumentProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"superagent.document.task"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
        "super-agent.messaging.kafka-enabled=true",
        "super-agent.messaging.document-task-topic=superagent.document.task",
        "spring.kafka.consumer.group-id=superagent-document-worker-it"
})
class DocumentTaskConsumerKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, DocumentTaskMessage> kafkaTemplate;

    @MockBean
    private DocumentProcessingService documentProcessingService;

    @Test
    void shouldConsumeKafkaMessageAndInvokeProcessingService() {
        DocumentTaskMessage message = new DocumentTaskMessage(10001L, 60001L, 80001L, "embedded-kafka");

        kafkaTemplate.send("superagent.document.task", String.valueOf(message.documentId()), message);

        verify(documentProcessingService, timeout(10_000)).process(argThat(candidate ->
                candidate.tenantId() == 10001L
                        && candidate.documentId() == 60001L
                        && candidate.taskId() == 80001L
                        && "embedded-kafka".equals(candidate.trigger())
        ));
    }
}
