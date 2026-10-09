package br.com.condominioauditoria.api.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/** Settings of the api service (the "condominio" block of application.yml). */
@ConfigurationProperties(prefix = "condominio")
public record ApiProperties(StorageProperties storage, ProcessingProperties processing, GrpcProperties grpc,
        RagProperties rag) {

    /** type = local in the MVP; the cloud adds another type (e.g. s3) without changing the code that uses it. */
    public record StorageProperties(String type, String folder) {
    }

    /** A file stalled in the queue for longer than resendAfterMinutes is sent to the rag again, up to maxAttempts. */
    public record ProcessingProperties(int resendAfterMinutes, int maxAttempts) {
    }

    /** Port of the query gRPC server (used by the mcp service). */
    public record GrpcProperties(int port) {
    }

    /**
     * gRPC client of the assistant in the rag (contracts/grpc/assistente/v1): host:port address and timeouts.
     * timeoutSeconds applies to Buscar and ListarProvedores; questionTimeoutSeconds to Perguntar (the model may take
     * longer, with tools and a retry). The provider catalog (ListarProvedores) is kept in memory for
     * catalogCacheSeconds.
     */
    public record RagProperties(String grpc, int timeoutSeconds, int questionTimeoutSeconds, int catalogCacheSeconds) {

        public static final int DEFAULT_QUESTION_TIMEOUT = 120;
        public static final int DEFAULT_CATALOG_CACHE = 300;

        @ConstructorBinding
        public RagProperties {
            questionTimeoutSeconds = questionTimeoutSeconds > 0 ? questionTimeoutSeconds : DEFAULT_QUESTION_TIMEOUT;
            catalogCacheSeconds = catalogCacheSeconds >= 0 ? catalogCacheSeconds : DEFAULT_CATALOG_CACHE;
        }

        public RagProperties(String grpc, int timeoutSeconds) {
            this(grpc, timeoutSeconds, DEFAULT_QUESTION_TIMEOUT, DEFAULT_CATALOG_CACHE);
        }
    }
}
