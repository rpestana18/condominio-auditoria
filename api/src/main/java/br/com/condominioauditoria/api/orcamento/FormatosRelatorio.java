package br.com.condominioauditoria.api.orcamento;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Máscaras usadas no template do PDF (as mesmas do Excel e da tela): só formata, nunca calcula. */
public final class FormatosRelatorio {

    public String dinheiro(BigDecimal v) {
        return RelatorioPrevistoRealizado.dinheiro(v);
    }

    public String percentual(BigDecimal v) {
        return RelatorioPrevistoRealizado.percentual(v);
    }

    public String data(LocalDate d) {
        return RelatorioPrevistoRealizado.data(d);
    }

    public String mes(String aaaaMm) {
        return RelatorioPrevistoRealizado.mes(aaaaMm);
    }

    public String contas(java.util.List<String> contas) {
        return contas == null || contas.isEmpty() ? "" : String.join(" ", contas);
    }

    public String simNao(Boolean v) {
        return v == null ? "—" : v ? "sim" : "não";
    }
}
