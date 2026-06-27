package com.superagent.agent.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Configuration
public class AgentTaskExecutorConfiguration {

    @Bean(name = "agentRunExecutor")
    public Executor agentRunExecutor() {
        return new SimpleAsyncTaskExecutor("agent-run-");
    }
}
