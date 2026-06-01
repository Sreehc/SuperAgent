package com.superagent.knowledge.messaging;

import com.superagent.knowledge.service.DocumentProcessingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "super-agent.messaging", name = "kafka-enabled", havingValue = "true")
public class DocumentTaskConsumer {

    private final DocumentProcessingService documentProcessingService;

    public DocumentTaskConsumer(DocumentProcessingService documentProcessingService) {
        this.documentProcessingService = documentProcessingService;
    }

    @KafkaListener(
            topics = "${super-agent.messaging.document-task-topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(DocumentTaskMessage message) {
        documentProcessingService.process(message);
    }
}
