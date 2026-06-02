package com.superagent.agent.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "agent")
public class AgentServiceProperties {

    @NotBlank
    private String pluginManifestRoot = "../plugins";

    @NotBlank
    private String sandboxRunnerBaseUrl = "http://localhost:18082";

    @NotBlank
    private String backendBaseUrl = "http://localhost:8080";

    private final Graph graph = new Graph();

    public String getPluginManifestRoot() {
        return pluginManifestRoot;
    }

    public void setPluginManifestRoot(String pluginManifestRoot) {
        this.pluginManifestRoot = pluginManifestRoot;
    }

    public String getSandboxRunnerBaseUrl() {
        return sandboxRunnerBaseUrl;
    }

    public void setSandboxRunnerBaseUrl(String sandboxRunnerBaseUrl) {
        this.sandboxRunnerBaseUrl = sandboxRunnerBaseUrl;
    }

    public String getBackendBaseUrl() {
        return backendBaseUrl;
    }

    public void setBackendBaseUrl(String backendBaseUrl) {
        this.backendBaseUrl = backendBaseUrl;
    }

    public Graph getGraph() {
        return graph;
    }

    public static class Graph {

        @NotBlank
        private String neo4jUri = "bolt://localhost:7687";

        @NotBlank
        private String neo4jUsername = "neo4j";

        @NotBlank
        private String neo4jPassword = "password";

        private boolean enabled = false;

        public String getNeo4jUri() {
            return neo4jUri;
        }

        public void setNeo4jUri(String neo4jUri) {
            this.neo4jUri = neo4jUri;
        }

        public String getNeo4jUsername() {
            return neo4jUsername;
        }

        public void setNeo4jUsername(String neo4jUsername) {
            this.neo4jUsername = neo4jUsername;
        }

        public String getNeo4jPassword() {
            return neo4jPassword;
        }

        public void setNeo4jPassword(String neo4jPassword) {
            this.neo4jPassword = neo4jPassword;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
