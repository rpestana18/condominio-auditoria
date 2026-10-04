package br.com.condominioauditoria.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Parâmetros do serviço rag (bloco "rag" do application.yml). */
@ConfigurationProperties(prefix = "rag")
public record PropriedadesRag(Armazenamento armazenamento, Leitor leitor, Grpc grpc, Indexacao indexacao,
        Embeddings embeddings) {

    /** Mesma pasta (ou bucket) onde o backend guarda os originais; o rag só lê. */
    public record Armazenamento(String tipo, String pasta) {
    }

    public record Leitor(String url, int timeoutSegundos) {
    }

    /** Servidor gRPC do assistente (contracts/grpc/assistente/v1), só na rede interna. */
    public record Grpc(int porta) {
    }

    public record Indexacao(int paralelismo) {
    }

    /** Chamadas ao Ollama (embeddings locais, ADR 0003, Decisão 2). */
    public record Embeddings(int timeoutConexaoSegundos, int timeoutSegundos, int lote) {
    }
}
