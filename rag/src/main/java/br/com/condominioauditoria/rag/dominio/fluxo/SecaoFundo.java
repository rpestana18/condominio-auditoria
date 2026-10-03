package br.com.condominioauditoria.rag.dominio.fluxo;

import java.math.BigDecimal;
import java.util.List;

/** Bloco de um fundo no fluxo de caixa: saldo anterior, lançamentos e a linha TOTAIS do próprio relatório. */
public record SecaoFundo(
        String fundo,
        BigDecimal saldoAnterior,
        List<LancamentoFluxo> lancamentos,
        BigDecimal totalCreditosInformado,
        BigDecimal totalDebitosInformado) {
}
