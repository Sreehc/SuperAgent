package com.superagent.knowledge;

import static org.mockito.Mockito.verify;

import com.superagent.knowledge.messaging.DocumentTaskConsumer;
import com.superagent.knowledge.messaging.DocumentTaskMessage;
import com.superagent.knowledge.service.DocumentProcessingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentTaskConsumerTest {

    @Mock
    private DocumentProcessingService documentProcessingService;

    @InjectMocks
    private DocumentTaskConsumer documentTaskConsumer;

    @Test
    void shouldDelegateKafkaMessageToDocumentProcessingService() {
        DocumentTaskMessage message = new DocumentTaskMessage(10001L, 60001L, 80001L, "upload");

        documentTaskConsumer.consume(message);

        verify(documentProcessingService).process(message);
    }
}
