package br.com.condominioauditoria.api.modulo;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

/** Função registrada no uso de um módulo (RF-09.7). No banco e na API vai o código em minúsculas. */
public enum FuncaoUso {
    /** Busca nos documentos feita pela tela (API REST do assistente, a partir da entrega 3). */
    BUSCA_DOCUMENTOS("busca_documentos"),
    /** buscar_documentos chamado pelo MCP (rpc BuscarDocumentos). */
    CHAMADA_MCP("chamada_mcp"),
    /** Um arquivo indexado (arquivos = 1, páginas lidas). */
    INDEXACAO("indexacao"),
    /** Geração de embeddings fora da indexação (ex.: da pergunta do chat; entrega 3). */
    EMBEDDINGS("embeddings"),
    /** Pergunta ao chat (entrega 3). */
    PERGUNTA("pergunta");

    private final String codigo;

    FuncaoUso(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static FuncaoUso deCodigo(String codigo) {
        return Arrays.stream(values()).filter(f -> f.codigo.equals(codigo)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Função de uso desconhecida: " + codigo));
    }

    @Converter(autoApply = true)
    static class Conversor implements AttributeConverter<FuncaoUso, String> {

        @Override
        public String convertToDatabaseColumn(FuncaoUso funcao) {
            return funcao == null ? null : funcao.codigo;
        }

        @Override
        public FuncaoUso convertToEntityAttribute(String codigo) {
            return codigo == null ? null : deCodigo(codigo);
        }
    }
}
