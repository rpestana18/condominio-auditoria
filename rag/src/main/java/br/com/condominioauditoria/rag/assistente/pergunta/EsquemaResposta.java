package br.com.condominioauditoria.rag.assistente.pergunta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Esquema JSON pedido ao modelo em {@code output_config.format} (saída estruturada) e leitura do que volta
 * (ADR 0003, Decisão 1 e especificação da entrega 3).
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
public final class EsquemaResposta {

    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private EsquemaResposta() {
    }

    /** Um parágrafo do bloco "Nos documentos", com os trechos que o sustentam. */
    public record ParagrafoModelo(String texto, List<String> trechoIds) {
    }

    /** Referência a uma chamada de ferramenta desta pergunta; os números são escritos pelo rag, não aqui. */
    public record ComentarioDado(String chamadaId, String comentario) {
    }

    public record RespostaModelo(List<ParagrafoModelo> nosDocumentos, List<ComentarioDado> nosDadosGravados,
            boolean naoEncontrado, String sugestao) {

        public boolean vazia() {
            return nosDocumentos.isEmpty() && nosDadosGravados.isEmpty();
        }
    }

    /** JSON fora do esquema = falha de redação, tratada como validação reprovada. */
    public static class RespostaIlegivelException extends RuntimeException {
        public RespostaIlegivelException(String mensagem) {
            super(mensagem);
        }
    }

    public static Map<String, Object> esquema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("nosDocumentos", "nosDadosGravados", "naoEncontrado", "sugestao"),
                "properties", propriedades());
    }

    private static Map<String, Object> propriedades() {
        Map<String, Object> propriedades = new LinkedHashMap<>();
        propriedades.put("nosDocumentos", Map.of(
                "type", "array",
                "description", "Parágrafos apoiados nos trechos dos documentos, na ordem de leitura.",
                "items", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "required", List.of("texto", "trechoIds"),
                        "properties", Map.of(
                                "texto", Map.of("type", "string",
                                        "description", "Parágrafo em português. Número transcrito do documento só "
                                                + "com a marca \"" + InstrucoesAssistente.MARCA_NAO_CONFERIDO
                                                + "\" no fim do parágrafo."),
                                "trechoIds", Map.of("type", "array", "minItems", 1,
                                        "description", "trechoIds que sustentam este parágrafo.",
                                        "items", Map.of("type", "string"))))));
        propriedades.put("nosDadosGravados", Map.of(
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
        propriedades.put("naoEncontrado", Map.of("type", "boolean",
                "description", "true quando os trechos e as ferramentas não respondem a pergunta."));
        propriedades.put("sugestao", Map.of("type", "string",
                "description", "Documento que faltaria para responder, em uma frase. \"\" quando não se aplica."));
        return propriedades;
    }

    public static RespostaModelo ler(String json) {
        if (json == null || json.isBlank()) {
            throw new RespostaIlegivelException("o modelo não devolveu texto");
        }
        JsonNode raiz;
        try {
            raiz = JSON.readTree(json);
        } catch (JacksonException erro) {
            throw new RespostaIlegivelException("a resposta do modelo não é um JSON válido");
        }
        if (!raiz.isObject()) {
            throw new RespostaIlegivelException("a resposta do modelo não é um objeto JSON");
        }
        return new RespostaModelo(paragrafos(raiz.path("nosDocumentos")), dados(raiz.path("nosDadosGravados")),
                raiz.path("naoEncontrado").asBoolean(false), texto(raiz.path("sugestao")));
    }

    private static List<ParagrafoModelo> paragrafos(JsonNode no) {
        if (!no.isArray()) {
            return List.of();
        }
        return no.valueStream().map(item -> new ParagrafoModelo(texto(item.path("texto")),
                item.path("trechoIds").valueStream().map(EsquemaResposta::texto).filter(t -> !t.isBlank()).toList()))
                .toList();
    }

    private static List<ComentarioDado> dados(JsonNode no) {
        if (!no.isArray()) {
            return List.of();
        }
        return no.valueStream()
                .map(item -> new ComentarioDado(texto(item.path("chamadaId")), texto(item.path("comentario"))))
                .toList();
    }

    private static String texto(JsonNode no) {
        return no.isString() ? no.asString() : "";
    }
}
