package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sugestão da rubrica de cada linha de uma PO (RF-11.7; ADR 0005, Decisão 1). Função pura, sem IA.
 *
 * <p>Regras, nesta ordem:
 * <ol>
 * <li>Duas ou mais linhas desta PO com a mesma conta no mesmo grupo: sem sugestão (somá-las exige o Admin).</li>
 * <li>Linha igual (mesmo código efetivo e mesma conta) confirmada na versão anterior do mesmo exercício: a rubrica
 * dela, origem {@code VERSAO_ANTERIOR}.</li>
 * <li>Linhas confirmadas de outras POs com a mesma conta da PO e o mesmo grupo: se todas estão numa só rubrica, ela,
 * origem {@code CONTA_PO}; se estão em mais de uma, ou não há nenhuma, sem sugestão.</li>
 * </ol>
 * Nunca usa o código do item (muda entre POs). A descrição, que costuma ser o fornecedor, só entra quando a linha não
 * tem conta nem texto na coluna de conta.
 */
final class SugestaoRubrica {

    private SugestaoRubrica() {
    }

    /** Linha de despesa ou de fundo com o código do grupo onde está (nulo para linha fora de grupo). */
    record LinhaComGrupo(BudgetLine linha, String grupo) {

        /** O que é comparado: a conta da PO, senão o texto da coluna de conta, senão a descrição. */
        String rotulo() {
            return rotuloDe(linha);
        }

        String chave() {
            return BudgetStructure.normalize(rotulo()).replaceAll("\\s+", " ");
        }

        String chaveComGrupo() {
            return chave() + "|" + (grupo == null ? "" : grupo);
        }
    }

    /** Linha de outra PO com rubrica confirmada. */
    record Confirmada(LinhaComGrupo linha, UUID rubricaId) {
    }

    sealed interface Resultado {
        String motivo();
    }

    record Sugerida(UUID rubricaId, OrigemRubrica origem, String motivo) implements Resultado {
    }

    record SemSugestao(String motivo) implements Resultado {
    }

    /** Linhas que recebem rubrica: todas as do tipo LINHA, de despesa e de fundo, na ordem da PO. */
    static List<LinhaComGrupo> linhas(BudgetStructure estrutura) {
        List<LinhaComGrupo> todas = new ArrayList<>();
        estrutura.ungrouped().forEach(l -> todas.add(new LinhaComGrupo(l, null)));
        for (BudgetStructure.Group g : estrutura.groups()) {
            g.lines().forEach(l -> todas.add(new LinhaComGrupo(l, g.line().getEffectiveCode())));
        }
        return todas;
    }

    static String rotuloDe(BudgetLine l) {
        if (temTexto(l.getAccount())) {
            return l.getAccount().trim();
        }
        if (temTexto(l.getAccountText())) {
            return l.getAccountText().trim();
        }
        return l.getDescription() == null ? "" : l.getDescription().trim();
    }

    /**
     * @param novas linhas a sugerir (as que ainda não têm rubrica nesta PO)
     * @param todasDaPo todas as linhas desta PO (para achar contas repetidas no mesmo grupo)
     * @param versaoAnterior linhas confirmadas da versão anterior do mesmo exercício (vazia se não há)
     * @param outrasPos linhas confirmadas de todas as outras POs do condomínio
     * @return resultado por id da linha, na ordem de {@code novas}
     */
    static Map<UUID, Resultado> sugerir(List<LinhaComGrupo> novas, List<LinhaComGrupo> todasDaPo,
            List<Confirmada> versaoAnterior, List<Confirmada> outrasPos) {
        Map<String, Integer> repetidas = new HashMap<>();
        todasDaPo.forEach(l -> repetidas.merge(l.chaveComGrupo(), 1, Integer::sum));

        Map<String, UUID> daAnterior = new HashMap<>();
        for (Confirmada c : versaoAnterior) {
            daAnterior.put(c.linha().linha().getEffectiveCode() + "|" + c.linha().chave(), c.rubricaId());
        }
        Map<String, Set<UUID>> porContaEGrupo = new HashMap<>();
        for (Confirmada c : outrasPos) {
            porContaEGrupo.computeIfAbsent(c.linha().chaveComGrupo(), k -> new LinkedHashSet<>()).add(c.rubricaId());
        }

        Map<UUID, Resultado> resultado = new LinkedHashMap<>();
        for (LinhaComGrupo l : novas) {
            resultado.put(l.linha().getId(), sugerir(l, repetidas, daAnterior, porContaEGrupo));
        }
        return resultado;
    }

    private static Resultado sugerir(LinhaComGrupo l, Map<String, Integer> repetidas, Map<String, UUID> daAnterior,
            Map<String, Set<UUID>> porContaEGrupo) {
        String grupo = l.grupo() == null ? "sem grupo" : l.grupo();
        String oQue = descricaoDoCampo(l.linha());
        if (l.chave().isEmpty()) {
            return new SemSugestao("linha sem conta da PO nem descrição: escolha a rubrica na lista");
        }
        if (repetidas.getOrDefault(l.chaveComGrupo(), 0) > 1) {
            return new SemSugestao(oQue + " \"" + l.rotulo() + "\" aparece em mais de uma linha do grupo " + grupo
                    + " nesta PO: escolha a rubrica de cada linha");
        }
        UUID anterior = daAnterior.get(l.linha().getEffectiveCode() + "|" + l.chave());
        if (anterior != null) {
            return new Sugerida(anterior, OrigemRubrica.VERSAO_ANTERIOR, "linha igual na versão anterior: "
                    + l.linha().getEffectiveCode() + " " + l.rotulo());
        }
        Set<UUID> rubricas = porContaEGrupo.getOrDefault(l.chaveComGrupo(), Set.of());
        if (rubricas.size() == 1) {
            return new Sugerida(rubricas.iterator().next(), OrigemRubrica.CONTA_PO,
                    mesmoCampo(l.linha()) + " e mesmo grupo: " + l.rotulo() + ", " + grupo);
        }
        if (rubricas.size() > 1) {
            return new SemSugestao(oQue + " \"" + l.rotulo() + "\" no grupo " + grupo + " está em " + rubricas.size()
                    + " rubricas: escolha na lista");
        }
        return new SemSugestao("nenhuma linha confirmada com " + oQue + " \"" + l.rotulo() + "\" no grupo " + grupo
                + ": escolha na lista ou crie uma rubrica");
    }

    /** "a conta da PO", "o texto da conta" ou "a descrição", conforme o campo comparado. */
    private static String descricaoDoCampo(BudgetLine l) {
        if (temTexto(l.getAccount())) {
            return "a conta da PO";
        }
        if (temTexto(l.getAccountText())) {
            return "o texto da conta";
        }
        return "a descrição";
    }

    /** "mesma conta da PO", "mesmo texto da conta" ou "mesma descrição" (início do motivo da sugestão). */
    private static String mesmoCampo(BudgetLine l) {
        if (temTexto(l.getAccount())) {
            return "mesma conta da PO";
        }
        if (temTexto(l.getAccountText())) {
            return "mesmo texto da conta";
        }
        return "mesma descrição";
    }

    private static boolean temTexto(String s) {
        return s != null && !s.isBlank();
    }
}
