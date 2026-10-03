package br.com.condominioauditoria.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Parâmetros do serviço mcp (bloco "mcp" do application.yml). */
@ConfigurationProperties(prefix = "mcp")
public record PropriedadesMcp(Backend backend) {

    /** Endereço gRPC do backend (ex.: backend:9090) e tempo máximo de cada chamada. */
    public record Backend(String endereco, int prazoSegundos) {
    }
}
