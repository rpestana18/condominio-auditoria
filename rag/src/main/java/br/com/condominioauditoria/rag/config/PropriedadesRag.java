package br.com.condominioauditoria.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Parâmetros do serviço rag (bloco "rag" do application.yml). */
@ConfigurationProperties(prefix = "rag")
public record PropriedadesRag(Armazenamento armazenamento, Leitor leitor) {

    /** Mesma pasta (ou bucket) onde o backend guarda os originais; o rag só lê. */
    public record Armazenamento(String tipo, String pasta) {
    }

    public record Leitor(String url, int timeoutSegundos) {
    }
}
