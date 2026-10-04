package br.com.condominioauditoria.rag.leitura.fluxo;

import br.com.condominioauditoria.rag.dominio.fluxo.LancamentoFluxo.Enriquecimento;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tira do histórico o que dá para deduzir com segurança: nota fiscal, fornecedor, meio de pagamento, transferência
 * e recebimento de cota.
 */
final class Enriquecedor {

    private static final Pattern NOTA_FISCAL = Pattern.compile("\\b(?:NF|NOTA FISCAL):?\\s*(\\d{1,10})");
    private static final Pattern FORNECEDOR = Pattern.compile("\\bDE:\\s*(.+?)\\s*(?:\\(\\d+\\))?$");
    private static final Pattern MEIO_PAGAMENTO = Pattern.compile("MERCADO PAGO|CART[ÃA]O DE CR[ÉE]DITO|PICPAY|PAGSEGURO");

    /** Histórico dos créditos de cota no layout Protest: os recibos pagos no dia, sem conta contábil. */
    static final String RECIBOS_ACUMULADOS = "RECIBOS ACUMULADOS";

    private Enriquecedor() {
    }

    static Enriquecimento enriquecer(String contaNome, String historico, BigDecimal credito, BigDecimal debito) {
        String nf = primeiroGrupo(NOTA_FISCAL, historico);
        String fornecedor = primeiroGrupo(FORNECEDOR, historico);
        Matcher meio = MEIO_PAGAMENTO.matcher(historico);
        boolean transferencia = contaNome.contains("TRANSFERENCIA CONTABIL") || contaNome.contains("AJUSTE CONTABIL")
                || historico.startsWith("TRANSFERENCIA DE ");
        return new Enriquecimento(nf, fornecedor, meio.find() ? meio.group() : null, transferencia,
                recebimentoCota(historico, credito, debito));
    }

    /**
     * Crédito "RECIBOS ACUMULADOS" (ADR 0004, Decisão 7). Só a coluna de crédito conta: o valor pode ser negativo
     * (estorno de recibo, como no FUNDO INADIMPLENTES), e linha com débito nunca é recebimento de cota.
     */
    static boolean recebimentoCota(String historico, BigDecimal credito, BigDecimal debito) {
        return RECIBOS_ACUMULADOS.equals(historico.trim()) && credito.signum() != 0 && debito.signum() == 0;
    }

    private static String primeiroGrupo(Pattern padrao, String texto) {
        Matcher m = padrao.matcher(texto);
        return m.find() ? m.group(1) : null;
    }
}
