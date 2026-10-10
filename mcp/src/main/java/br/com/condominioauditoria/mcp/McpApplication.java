package br.com.condominioauditoria.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * mcp service: entry point of an external AI (in the pilot, the user's Claude) through the Model Context Protocol.
 * It has no database and no rules: each tool becomes a gRPC call to the api, with the user's own token, so the AI
 * only sees what the user can see.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class McpApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpApplication.class, args);
    }
}
