package br.com.condominioauditoria.logging;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * Log context of the Java services (ADR 0008): the request id that follows a request across HTTP, gRPC and the queue,
 * and the MDC keys every service uses. Keys in English (ADR 0006); log messages stay in Portuguese.
 *
 * <p>Never put in the MDC: tokens, keys, names of people, the original file name, document content, money amounts or
 * free text typed by the user. The user is the Keycloak {@code sub} (a pseudonym), never the username.
 */
public final class LogContext {

    /** HTTP header (frontend → api, rag → reader, MCP client → mcp), also returned in the api responses. */
    public static final String HTTP_HEADER = "X-Request-Id";
    /** gRPC metadata key (mcp → api, api ↔ rag); lowercase, as gRPC requires. */
    public static final String GRPC_KEY = "x-request-id";
    /** AMQP header of the queue messages (outside the body: the contracts of the bodies do not change). */
    public static final String AMQP_HEADER = "requestId";

    public static final String REQUEST_ID = "requestId";
    public static final String USER = "user";
    public static final String CONDOMINIUM_ID = "condominiumId";
    public static final String FILE_ID = "fileId";
    public static final String PROCESSING_ID = "processingId";
    public static final String INDEXING_ID = "indexingId";

    /** Accepted request id: letters, digits, dot, dash and underscore, up to 64 characters (no log injection). */
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private LogContext() {
    }

    public static String newRequestId() {
        return UUID.randomUUID().toString();
    }

    /** The received id when it is valid, otherwise a new one. */
    public static String acceptOrCreate(String received) {
        return isValid(received) ? received : newRequestId();
    }

    public static boolean isValid(String requestId) {
        return requestId != null && VALID.matcher(requestId).matches();
    }

    /** Request id of the current thread, or null outside a request. */
    public static String currentRequestId() {
        return MDC.get(REQUEST_ID);
    }

    /** Request id of the current thread, or a new one (e.g. a message published by a scheduled scan). */
    public static String currentOrNewRequestId() {
        String current = currentRequestId();
        return isValid(current) ? current : newRequestId();
    }

    /** Puts one key in the MDC until the scope closes; null values are skipped. */
    public static Scope with(String key, Object value) {
        Map<String, Object> values = new HashMap<>();
        values.put(key, value);
        return with(values);
    }

    /**
     * Puts the keys in the MDC until the scope closes, then restores what was there before (the threads of the
     * queue and gRPC pools are reused). Null values are skipped.
     */
    public static Scope with(Map<String, ?> values) {
        Map<String, String> previous = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (value != null) {
                previous.put(key, MDC.get(key));
                MDC.put(key, value.toString());
            }
        });
        return new Scope(previous);
    }

    /** MDC values put by {@link #with}; closing restores the previous ones. */
    public static final class Scope implements AutoCloseable {

        private final Map<String, String> previous;

        private Scope(Map<String, String> previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            previous.forEach((key, value) -> {
                if (value == null) {
                    MDC.remove(key);
                } else {
                    MDC.put(key, value);
                }
            });
        }
    }
}
