package br.com.condominioauditoria.rag.dominio.po;

import java.math.BigDecimal;
import java.util.List;

/**
 * PO aprovada, já lida, como está no documento: cada linha com o código como impresso e os valores lidos, sem nenhum
 * juízo. Mesmo formato do bloco previsaoOrcamentaria de contracts/mensagens/v2 (ADR 0004, Decisão 2).
 *
 * @param colunasOrcado rótulos impressos das colunas de orçado, na ordem [anterior, exercício]
 */
public record PrevisaoOrcamentaria(
        String titulo,
        String exercicioImpresso,
        List<String> colunasOrcado,
        List<LinhaPo> linhas) {

    /** TOTAL (código 1), GRUPO (dois níveis, ex.: 1.3) ou LINHA (três níveis, ex.: 1.3.20). */
    public enum TipoLinha {
        TOTAL, GRUPO, LINHA
    }

    /** Marca lida na coluna de conta (ou "Rateio à parte" nas Observações), no lugar de uma conta. */
    public enum Marca {
        RATEIO_A_PARTE, NEGOCIADA_ISENCAO, SEM_VALOR, VALOR_FIXO_SEM_REFERENCIA
    }

    /**
     * Uma linha da PO. {@code ordem} é a ordem de leitura e distingue linhas com o mesmo código impresso.
     * "%" e Observações ficam como texto lido e não entram em cálculo.
     */
    public record LinhaPo(
            int ordem,
            int pagina,
            TipoLinha tipo,
            String codigoImpresso,
            String conta,
            String contaTexto,
            Marca marca,
            String descricao,
            BigDecimal orcadoAnterior,
            BigDecimal orcado,
            String percentualTexto,
            String observacoes) {
    }
}
