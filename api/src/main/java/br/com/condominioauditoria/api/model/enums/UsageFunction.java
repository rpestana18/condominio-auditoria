package br.com.condominioauditoria.api.model.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

/** Function recorded in a feature's usage (RF-09.7). The database and the API store the lowercase code. */
public enum UsageFunction {
    /** Document search made from the screen (the assistant's REST API, from delivery 3 on). */
    BUSCA_DOCUMENTOS("busca_documentos"),
    /** buscar_documentos called by the MCP (rpc BuscarDocumentos). */
    CHAMADA_MCP("chamada_mcp"),
    /** One indexed file (files = 1, pages read). */
    INDEXACAO("indexacao"),
    /** Embedding generation outside indexing (e.g. of the chat question; delivery 3). */
    EMBEDDINGS("embeddings"),
    /** Question to the chat (delivery 3). */
    PERGUNTA("pergunta");

    private final String code;

    UsageFunction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static UsageFunction fromCode(String code) {
        return Arrays.stream(values()).filter(f -> f.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Função de uso desconhecida: " + code));
    }

    @Converter(autoApply = true)
    static class DbConverter implements AttributeConverter<UsageFunction, String> {

        @Override
        public String convertToDatabaseColumn(UsageFunction function) {
            return function == null ? null : function.code;
        }

        @Override
        public UsageFunction convertToEntityAttribute(String code) {
            return code == null ? null : fromCode(code);
        }
    }
}
