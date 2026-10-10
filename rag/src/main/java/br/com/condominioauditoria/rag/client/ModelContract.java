package br.com.condominioauditoria.rag.client;

import java.util.List;
import java.util.Map;

/**
 * Neutral types of the ai-gateway (RF-09.3): this is how the rest of the rag talks to any model. No provider SDK class
 * crosses this boundary, and only the gateway receives the opened API key.
 */
public final class ModelContract {

    private ModelContract() {
    }

    /**
     * A tool offered to the model.
     *
     * @param inputSchema JSON schema of the arguments ({@code properties}, {@code required})
     */
    public record ToolDefinition(String name, String description, Map<String, Object> inputSchema) {
    }

    /** The model requested a tool. */
    public record ToolCall(String id, String name, Map<String, Object> arguments) {
    }

    /** Result returned to the model. {@code error} marks the call as failed (is_error). */
    public record ToolResult(String id, String content, boolean error) {
    }

    /** Why the model stopped. */
    public enum Stop {
        /** Final answer ready (end_turn, stop_sequence). */
        END,
        /** Requested at least one tool. */
        TOOL_USE,
        /** Model safety refusal (stop_reason = refusal): becomes NOT_FOUND with a warning, no retry. */
        REFUSAL,
        /** Token ceiling or context window: treated as a drafting failure. */
        TRUNCATED
    }

    /**
     * One round of conversation with the model.
     *
     * @param jsonText response text (JSON of the schema) when {@link Stop#END}
     * @param calls tools requested when {@link Stop#TOOL_USE}
     * @param refusalExplanation explanation of the refusal, when there is one
     */
    public record Turn(Stop stop, String jsonText, List<ToolCall> calls, String refusalExplanation) {
    }

    /**
     * Parameters of a question to the provider. The API key lives only here and in the client created for this
     * question.
     *
     * @param effort {@code output_config.effort}; null or empty = not sent (Haiku 4.5 rejects effort)
     * @param outputSchema JSON schema requested in {@code output_config.format}
     */
    public record Parameters(String providerType, String model, String apiKey, String instructions, int maxTokens,
            String effort, Map<String, Object> outputSchema, List<ToolDefinition> tools) {
    }

    /** Provider failure already classified; the gRPC layer translates it into a status of the specification. */
    public static class ProviderErrorException extends RuntimeException {

        public enum Kind {
            /** HTTP 401 or 403: the condominium's key was rejected. */
            KEY_REJECTED,
            /** HTTP 429. */
            RATE_LIMITED,
            /** 5xx, connection failure or deadline exceeded. */
            UNAVAILABLE,
            /** Request rejected by the provider (400): our error, not the user's. */
            INVALID_REQUEST
        }

        private final Kind kind;

        public ProviderErrorException(Kind type, String message, Throwable cause) {
            super(message, cause);
            this.kind = type;
        }

        public Kind kind() {
            return kind;
        }
    }
}
