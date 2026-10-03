package br.com.condominioauditoria.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Serviço mcp: porta de entrada de uma IA externa (no piloto, o Claude do usuário) via Model Context Protocol.
 * Não tem banco nem regra: cada ferramenta vira uma chamada gRPC ao backend, com o token do próprio usuário,
 * então a IA só vê o que o usuário pode ver.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class McpApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpApplication.class, args);
    }
}
