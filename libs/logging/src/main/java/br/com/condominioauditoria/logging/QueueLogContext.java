package br.com.condominioauditoria.logging;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Log context of a queue message (ADR 0008): the request id comes from the AMQP header {@code requestId} (or a new one)
 * and the business ids ({@code fileId}, {@code processingId}, {@code indexingId}, {@code condominiumId}) come from the
 * body. Only these ids are read, and only when they are UUIDs: nothing else of the body ever reaches the log (names,
 * entries and amounts stay out).
 */
public final class QueueLogContext {

    private static final List<String> KEYS = List.of(LogContext.FILE_ID, LogContext.PROCESSING_ID,
            LogContext.INDEXING_ID, LogContext.CONDOMINIUM_ID);
    private static final Map<String, Pattern> PATTERNS = new LinkedHashMap<>();

    static {
        for (String key : KEYS) {
            PATTERNS.put(key, Pattern.compile("\"" + key + "\"\\s*:\\s*\"([0-9a-fA-F-]{36})\""));
        }
    }

    private QueueLogContext() {
    }

    /** The ids found in the body (first occurrence of each key); empty for a body out of the contract. */
    public static Map<String, String> ids(byte[] body) {
        Map<String, String> ids = new LinkedHashMap<>();
        if (body == null) {
            return ids;
        }
        String text = new String(body, StandardCharsets.UTF_8);
        PATTERNS.forEach((key, pattern) -> {
            Matcher m = pattern.matcher(text);
            if (m.find()) {
                ids.put(key, m.group(1));
            }
        });
        return ids;
    }

    /** Request id of the header and the ids of the body in the MDC until the scope closes. */
    public static LogContext.Scope enter(byte[] body, Object requestIdHeader) {
        Map<String, String> values = ids(body);
        values.put(LogContext.REQUEST_ID, LogContext.acceptOrCreate(
                requestIdHeader == null ? null : requestIdHeader.toString()));
        return LogContext.with(values);
    }
}
