package br.com.condominioauditoria.ingestao.fluxo;

import br.com.condominioauditoria.dominio.fluxo.LancamentoFluxo.Enriquecimento;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tira do histórico o que dá para deduzir com segurança: nota fiscal, fornecedor, meio de pagamento, transferência. */
final class Enriquecedor {

    private static final Pattern NOTA_FISCAL = Pattern.compile("\\b(?:NF|NOTA FISCAL):?\\s*(\\d{1,10})");
    private static final Pattern FORNECEDOR = Pattern.compile("\\bDE:\\s*(.+?)\\s*(?:\\(\\d+\\))?$");
    private static final Pattern MEIO_PAGAMENTO = Pattern.compile("MERCADO PAGO|CART[ÃA]O DE CR[ÉE]DITO|PICPAY|PAGSEGURO");

    private Enriquecedor() {
    }

    static Enriquecimento enriquecer(String contaNome, String historico) {
        String nf = primeiroGrupo(NOTA_FISCAL, historico);
        String fornecedor = primeiroGrupo(FORNECEDOR, historico);
        Matcher meio = MEIO_PAGAMENTO.matcher(historico);
        boolean transferencia = contaNome.contains("TRANSFERENCIA CONTABIL") || contaNome.contains("AJUSTE CONTABIL")
                || historico.startsWith("TRANSFERENCIA DE ");
        return new Enriquecimento(nf, fornecedor, meio.find() ? meio.group() : null, transferencia);
    }

    private static String primeiroGrupo(Pattern padrao, String texto) {
        Matcher m = padrao.matcher(texto);
        return m.find() ? m.group(1) : null;
    }
}
