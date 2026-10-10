package br.com.condominioauditoria.mcp.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Parameters of the mcp service (block "mcp" of application.yml). */
@ConfigurationProperties(prefix = "mcp")
public record McpProperties(Api api) {

    /** gRPC address of the api (e.g. api:9090) and maximum time of each call. */
    public record Api(String address, int timeoutSeconds) {
    }
}
