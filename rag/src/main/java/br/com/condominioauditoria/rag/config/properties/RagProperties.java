package br.com.condominioauditoria.rag.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Parameters of the rag service (block "rag" of application.yml). */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(Storage storage, Reader reader, Grpc grpc, Indexing indexing,
        Embeddings embeddings, Assistant assistant) {

    /** Same folder (or bucket) where the api stores the originals; the rag only reads. */
    public record Storage(String type, String folder) {
    }

    public record Reader(String url, int timeoutSeconds) {
    }

    /** gRPC server of the assistant (contracts/grpc/assistant/v2), internal network only. */
    public record Grpc(int port) {
    }

    public record Indexing(int parallelism) {
    }

    /** Calls to Ollama (local embeddings, ADR 0003, Decision 2). */
    public record Embeddings(int connectTimeoutSeconds, int timeoutSeconds, int batchSize) {
    }

    /**
     * Assistant chat (ADR 0003, Decisions 1 and 5.2). Nothing here is condominium configuration: the mode, the
     * provider, the model and the encrypted key come in each Ask request, resolved by the api.
     *
     * @param privateKeyFile PKCS#8 PEM file with the rag's private key (RAG_CHAVE_PRIVADA_ARQUIVO)
     * @param generateDevKey generates the RSA 3072 pair when the file does not exist (development only)
     * @param anthropicUrl address of the Claude API (RAG_ANTHROPIC_URL)
     * @param providerTimeoutSeconds deadline of each call to the provider
     * @param providerAttempts HTTP client retries on network error or 5xx
     * @param maxTokens output token ceiling per round (no streaming)
     * @param effort output_config.effort (low, medium, high, xhigh, max); empty = not sent
     * @param toolRounds ceiling of rounds of the tool loop in one attempt
     * @param maxHistory how many previous exchanges of the conversation the rag uses
     * @param apiGrpc address of the api's Consulta gRPC (BACKEND_GRPC)
     * @param toolTimeoutSeconds deadline of each tool call to the api
     * @param conductTerms words the model cannot use outside a literal quotation (RF-04.15, Q11)
     */
    public record Assistant(String privateKeyFile, boolean generateDevKey, String anthropicUrl,
            int providerTimeoutSeconds, int providerAttempts, int maxTokens, String effort, int toolRounds,
            int maxHistory, String apiGrpc, int toolTimeoutSeconds, java.util.List<String> conductTerms) {
    }
}
