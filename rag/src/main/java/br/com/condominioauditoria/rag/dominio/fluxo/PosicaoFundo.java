package br.com.condominioauditoria.rag.dominio.fluxo;

import java.math.BigDecimal;

/** Uma linha do quadro "Posição Financeira" do fim do relatório. */
public record PosicaoFundo(String fundo, BigDecimal saldoAnterior, BigDecimal creditos, BigDecimal debitos, BigDecimal saldoAtual) {
}
