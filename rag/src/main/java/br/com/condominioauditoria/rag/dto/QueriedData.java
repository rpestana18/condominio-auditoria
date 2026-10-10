package br.com.condominioauditoria.rag.dto;

import java.util.List;

/**
 * Result of a numeric tool already formatted by the rag, the way it goes into the "Nos dados gravados" block
 * (RF-04.13, RF-04.14). The model never writes anything from here: it only references {@link #callId()}.
 *
 * @param callId identifier of the call in this question ("c1", "c2", ...)
 * @param query tool name (resumo_fundos, buscar_lancamentos, listar_arquivos, conferencias_do_arquivo)
 * @param params filters used, in the order they were passed
 * @param rows rows ready to display; money in reais in the Brazilian format
 */
public record QueriedData(String callId, String query, List<Param> params, List<Row> rows) {

    public record Param(String name, String value) {
    }

    public record Row(String label, String value) {
    }
}
