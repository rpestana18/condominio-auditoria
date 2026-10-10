package br.com.condominioauditoria.rag.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON schema requested from the model in {@code output_config.format} (structured output) and reading of what comes
 * back (ADR 0003, Decision 1 and delivery 3 specification).
 *
 * <pre>
 * {
 *   "nosDocumentos":     [ { "texto": "...", "trechoIds": ["..."] } ],
 *   "nosDadosGravados":  [ { "chamadaId": "c1", "comentario": "..." } ],
 *   "naoEncontrado":     false,
 *   "sugestao":          "..."
 * }
 * </pre>
 */
public final class ResponseSchema {

    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private ResponseSchema() {
    }

    /** A paragraph of the "Nos documentos" block, with the chunks that support it. */
    public record ModelParagraph(String text, List<String> chunkIds) {
    }

    /** Reference to a tool call of this question; the numbers are written by the rag, not here. */
    public record DataComment(String callId, String comment) {
    }

    public record ModelResponse(List<ModelParagraph> inDocuments, List<DataComment> inStoredData,
            boolean notFound, String suggestion) {

        public boolean isEmpty() {
            return inDocuments.isEmpty() && inStoredData.isEmpty();
        }
    }

    /** JSON out of the schema = drafting failure, treated as a failed validation. */
    public static class UnreadableResponseException extends RuntimeException {
        public UnreadableResponseException(String message) {
            super(message);
        }
    }

    public static Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("nosDocumentos", "nosDadosGravados", "naoEncontrado", "sugestao"),
                "properties", properties());
    }

    private static Map<String, Object> properties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("nosDocumentos", Map.of(
                "type", "array",
                "description", "Parágrafos apoiados nos trechos dos documentos, na ordem de leitura.",
                "items", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "required", List.of("texto", "trechoIds"),
                        "properties", Map.of(
                                "texto", Map.of("type", "string",
                                        "description", "Parágrafo em português. Número transcrito do documento só "
                                                + "com a marca \"" + AssistantInstructions.UNVERIFIED_MARK
                                                + "\" no fim do parágrafo."),
                                "trechoIds", Map.of("type", "array", "minItems", 1,
                                        "description", "trechoIds que sustentam este parágrafo.",
                                        "items", Map.of("type", "string"))))));
        properties.put("nosDadosGravados", Map.of(
                "type", "array",
                "description", "Consultas de dados gravados que respondem a pergunta. Os números são escritos pelo "
                        + "sistema a partir do resultado da ferramenta.",
                "items", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "required", List.of("chamadaId", "comentario"),
                        "properties", Map.of(
                                "chamadaId", Map.of("type", "string",
                                        "description", "Identificador da chamada de ferramenta desta pergunta."),
                                "comentario", Map.of("type", "string",
                                        "description", "Frase curta sobre o dado, SEM nenhum número. \"\" se não "
                                                + "houver nada a dizer.")))));
        properties.put("naoEncontrado", Map.of("type", "boolean",
                "description", "true quando os trechos e as ferramentas não respondem a pergunta."));
        properties.put("sugestao", Map.of("type", "string",
                "description", "Documento que faltaria para responder, em uma frase. \"\" quando não se aplica."));
        return properties;
    }

    public static ModelResponse read(String json) {
        if (json == null || json.isBlank()) {
            throw new UnreadableResponseException("o modelo não devolveu texto");
        }
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (JacksonException error) {
            throw new UnreadableResponseException("a resposta do modelo não é um JSON válido");
        }
        if (!root.isObject()) {
            throw new UnreadableResponseException("a resposta do modelo não é um objeto JSON");
        }
        return new ModelResponse(paragraphs(root.path("nosDocumentos")), dataComments(root.path("nosDadosGravados")),
                root.path("naoEncontrado").asBoolean(false), text(root.path("sugestao")));
    }

    private static List<ModelParagraph> paragraphs(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        return node.valueStream().map(item -> new ModelParagraph(text(item.path("texto")),
                item.path("trechoIds").valueStream().map(ResponseSchema::text).filter(t -> !t.isBlank()).toList()))
                .toList();
    }

    private static List<DataComment> dataComments(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        return node.valueStream()
                .map(item -> new DataComment(text(item.path("chamadaId")), text(item.path("comentario"))))
                .toList();
    }

    private static String text(JsonNode node) {
        return node.isString() ? node.asString() : "";
    }
}
