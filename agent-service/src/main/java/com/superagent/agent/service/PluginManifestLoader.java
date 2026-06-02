package com.superagent.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.agent.config.AgentServiceProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PluginManifestLoader {

    public PluginManifestLoader(
            AgentServiceProperties properties,
            ObjectMapper objectMapper,
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        Path root = Path.of(properties.getPluginManifestRoot());
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> files = Files.walk(root, 2)) {
            files.filter(path -> path.getFileName().toString().equals("manifest.json"))
                    .forEach(path -> upsertManifest(path, objectMapper, jdbcTemplate));
        } catch (Exception ignored) {
            // startup should not fail if manifests are absent or malformed
        }
    }

    private void upsertManifest(Path path, ObjectMapper objectMapper, NamedParameterJdbcTemplate jdbcTemplate) {
        try {
            JsonNode manifest = objectMapper.readTree(Files.readString(path));
            jdbcTemplate.update("""
                            INSERT INTO plugin_registry (plugin_key, version, display_name, manifest_json, status)
                            VALUES (:pluginKey, :version, :displayName, CAST(:manifestJson AS jsonb), 'active')
                            ON CONFLICT (plugin_key)
                            DO UPDATE SET version = EXCLUDED.version,
                                          display_name = EXCLUDED.display_name,
                                          manifest_json = EXCLUDED.manifest_json,
                                          updated_at = NOW()
                            """,
                    new MapSqlParameterSource()
                            .addValue("pluginKey", manifest.path("pluginId").asText(path.getParent().getFileName().toString()))
                            .addValue("version", manifest.path("version").asText("0.1.0"))
                            .addValue("displayName", manifest.path("displayName").asText(manifest.path("pluginId").asText()))
                            .addValue("manifestJson", objectMapper.writeValueAsString(manifest))
            );
        } catch (Exception ignored) {
            // malformed manifest is ignored for now
        }
    }
}
