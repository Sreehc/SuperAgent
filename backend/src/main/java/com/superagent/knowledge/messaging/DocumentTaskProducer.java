package com.superagent.knowledge.messaging;

import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.infra.config.SuperAgentProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "super-agent.messaging", name = "kafka-enabled", havingValue = "true")
public class DocumentTaskProducer {

    private final KafkaTemplate<String, DocumentTaskMessage> kafkaTemplate;
    private final SuperAgentProperties properties;

    public DocumentTaskProducer(
            KafkaTemplate<String, DocumentTaskMessage> kafkaTemplate,
            SuperAgentProperties properties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public void publish(DocumentTaskMessage message) {
        try {
            kafkaTemplate.send(
                    properties.getMessaging().getDocumentTaskTopic(),
                    String.valueOf(message.documentId()),
                    message
            ).get();
        } catch (Exception exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Failed to publish document task");
        }
    }
}
