package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.model.budget.BudgetLine;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Cópia do de-para da versão anterior da PO como sugestão (RF-03.1.4; ADR 0004, Decisão 4). Nenhuma conta herda a
 * confirmação. Linha igual = mesmo código efetivo, mesma conta da PO e mesma descrição; o valor orçado pode mudar.
 * Função pura.
 */
public final class CopiaVersaoAnterior {

    public sealed interface Resultado permits Copiada, NaoCopiada {
        String motivo();
    }

    /** @param igual a linha de destino é igual à da versão anterior (filtro "iguais à versão anterior") */
    public record Copiada(Destino destino, boolean igual, String motivo) implements Resultado {
    }

    public record NaoCopiada(String motivo) implements Resultado {
    }

    public static final String IGUAL = "igual à versão anterior";

    private CopiaVersaoAnterior() {
    }

    /**
     * @param anterior de-para confirmado da conta na versão anterior
     * @param linhasAnteriores linhas da versão anterior, pelo id
     * @param destinosNovos linhas de despesa da nova versão, pelo código efetivo
     */
    public static Resultado copiar(DeparaConta anterior, Map<UUID, BudgetLine> linhasAnteriores,
            Map<String, BudgetLine> destinosNovos) {
        if (anterior.getTipoDestino() != TipoDestino.LINHA_PO) {
            Destino d = anterior.destino();
            return new Copiada(d, true, IGUAL + ": " + d.texto());
        }
        BudgetLine antes = linhasAnteriores.get(anterior.getLinhaPoId());
        if (antes == null) {
            return new NaoCopiada("a linha de destino da versão anterior não foi encontrada");
        }
        BudgetLine agora = destinosNovos.get(antes.getEffectiveCode());
        if (agora == null) {
            return new NaoCopiada("a linha " + antes.getEffectiveCode() + " da versão anterior não existe nesta versão");
        }
        if (Objects.equals(antes.getAccount(), agora.getAccount())
                && Objects.equals(antes.getDescription(), agora.getDescription())) {
            return new Copiada(Destino.linha(agora), true, IGUAL + ": " + agora.getEffectiveCode() + " "
                    + agora.getDescription());
        }
        return new Copiada(Destino.linha(agora), false, "linha mudou: antes " + texto(antes) + "; agora " + texto(agora));
    }

    private static String texto(BudgetLine l) {
        return l.getEffectiveCode() + " " + (l.getAccount() == null ? "" : l.getAccount() + " / ") + l.getDescription();
    }
}
