package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.model.budget.BudgetLine;
import java.util.Objects;
import java.util.UUID;

/**
 * Destino de uma conta do fluxo: a linha da PO (pelo identificador interno, nunca pela conta da PO) ou um destino
 * especial com o texto lido, ex.: "AJUSTE (estorno)".
 *
 * @param linhaPoId linha da PO, só com {@link TipoDestino#LINHA_PO}
 * @param codigo código efetivo da linha, só para o texto (o casamento é pelo id)
 * @param descricao descrição da linha, só para o texto
 * @param detalhe texto entre parênteses do destino especial, ou nulo
 */
public record Destino(TipoDestino tipo, UUID linhaPoId, String codigo, String descricao, String detalhe) {

    public Destino {
        Objects.requireNonNull(tipo, "tipo");
        if ((tipo == TipoDestino.LINHA_PO) != (linhaPoId != null)) {
            throw new IllegalArgumentException("Destino LINHA_PO exige a linha da PO; os demais não têm linha");
        }
        detalhe = detalhe == null || detalhe.isBlank() ? null : detalhe.trim();
    }

    public static Destino linha(BudgetLine linha) {
        return new Destino(TipoDestino.LINHA_PO, linha.getId(), linha.getEffectiveCode(), linha.getDescription(), null);
    }

    public static Destino especial(TipoDestino tipo, String detalhe) {
        return new Destino(tipo, null, null, null, detalhe);
    }

    /** Mesmo destino (o texto da linha não conta, só o id). */
    public boolean mesmo(Destino outro) {
        return outro != null && tipo == outro.tipo && Objects.equals(linhaPoId, outro.linhaPoId)
                && Objects.equals(detalhe, outro.detalhe);
    }

    /** Como aparece na tela e na trilha, no formato da planilha: "1.7.8 Material hidráulico", "AJUSTE (estorno)". */
    public String texto() {
        if (tipo == TipoDestino.LINHA_PO) {
            return (codigo == null ? "linha " + linhaPoId : codigo) + (descricao == null ? "" : " " + descricao);
        }
        String rotulo = switch (tipo) {
            case AJUSTE -> "AJUSTE";
            case A_REALOCAR -> "REALOCAR";
            case TRANSFERENCIA -> "TRANSFERENCIA";
            case LINHA_PO -> throw new IllegalStateException();
        };
        return detalhe == null ? rotulo : rotulo + " (" + detalhe + ")";
    }
}
