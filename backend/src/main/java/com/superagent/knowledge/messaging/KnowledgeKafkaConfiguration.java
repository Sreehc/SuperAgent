package com.superagent.knowledge.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
@EnableKafka
@ConditionalOnProperty(prefix = "super-agent.messaging", name = "kafka-enabled", havingValue = "true")
public class KnowledgeKafkaConfiguration {
}
