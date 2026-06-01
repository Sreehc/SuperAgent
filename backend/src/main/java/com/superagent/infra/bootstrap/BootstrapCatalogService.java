package com.superagent.infra.bootstrap;

import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.infra.config.SuperAgentProperties;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BootstrapCatalogService {

    private static final List<ModuleDescriptor> MODULES = List.of(
            new ModuleDescriptor("api", "HTTP API, OpenAPI, and transport contracts"),
            new ModuleDescriptor("auth", "Authentication and tenant boundary placeholders"),
            new ModuleDescriptor("chat", "Conversation orchestration placeholders"),
            new ModuleDescriptor("rag", "Retrieval and RAG placeholders"),
            new ModuleDescriptor("knowledge", "Knowledge and document placeholders"),
            new ModuleDescriptor("observability", "Trace and audit placeholders"),
            new ModuleDescriptor("infra", "Database, messaging, storage, and runtime config"),
            new ModuleDescriptor("common", "Shared response, exception, and utility primitives")
    );

    private final SuperAgentProperties properties;

    public BootstrapCatalogService(SuperAgentProperties properties) {
        this.properties = properties;
    }

    public BootstrapSummary getSummary() {
        return new BootstrapSummary(
                properties.getApp().getName(),
                properties.getApp().getApiBasePath(),
                MODULES,
                new RuntimeDependencySummary(
                        properties.getStorage().getMinioBucket(),
                        properties.getStorage().getMinioEndpoint(),
                        properties.getMessaging().getBootstrapServers(),
                        properties.getMessaging().getDocumentTaskTopic(),
                        properties.getMessaging().getKafkaEnabled(),
                        properties.getAi().getChatModel(),
                        properties.getAi().getEmbeddingModel(),
                        properties.getAi().getRerankEnabled()
                )
        );
    }

    public ModuleDescriptor getModule(String name) {
        return MODULES.stream()
                .filter(module -> module.code().equals(name))
                .findFirst()
                .orElseThrow(() -> new AppException(
                        ErrorCode.NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Module not found: " + name
                ));
    }

    public record BootstrapSummary(
            String applicationName,
            String apiBasePath,
            List<ModuleDescriptor> modules,
            RuntimeDependencySummary runtimeDependencies
    ) {
    }

    public record ModuleDescriptor(String code, String description) {
    }

    public record RuntimeDependencySummary(
            String minioBucket,
            String minioEndpoint,
            String kafkaBootstrapServers,
            String documentTaskTopic,
            boolean kafkaEnabled,
            String chatModel,
            String embeddingModel,
            boolean rerankEnabled
    ) {
    }
}
