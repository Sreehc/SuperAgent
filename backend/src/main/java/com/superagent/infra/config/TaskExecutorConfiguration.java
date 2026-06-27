package com.superagent.infra.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Configuration
public class TaskExecutorConfiguration {

    @Bean(name = "conversationExecutor")
    public Executor conversationExecutor() {
        return new SimpleAsyncTaskExecutor("conversation-stream-");
    }
}
