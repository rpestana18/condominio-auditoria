package br.com.condominioauditoria.dominio.fluxo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma linha do fluxo de caixa, como está no relatório da administradora, com a origem (página e posição)
 * e os campos enriquecidos a partir do histórico.
 */
public record LancamentoFluxo(
        int pagina,
        int ordem,
        LocalDate data,
        String contaCodigo,
        String contaNome,
        String documento,
        String historico,
        BigDecimal credito,
        BigDecimal debito,
        BigDecimal saldo,
        Enriquecimento enriquecimento) {

    /** Informações deduzidas do histórico. Ficam separadas para ficar claro o que é original e o que é inferido. */
    public record Enriquecimento(String notaFiscal, String fornecedor, String meioPagamento, boolean transferenciaEntreFundos) {
    }
}
