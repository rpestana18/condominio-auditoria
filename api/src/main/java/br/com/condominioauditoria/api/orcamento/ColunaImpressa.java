package br.com.condominioauditoria.api.orcamento;

import static br.com.condominioauditoria.api.orcamento.DinheiroBr.formatar;

import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * "PO anterior pela coluna impressa" (RF-11.5; ADR 0005, Decisão 3): a coluna "Orçado anterior" de uma PO confirmada
 * vira um exercício só com previsto, montado na consulta, sem tabela. Função pura.
 *
 * <p>Conferência da coluna como no RF-03.1.2 (tolerância da PO, R$ 0,01): soma das linhas de cada grupo contra o
 * subtotal impresso, e total impresso contra a soma dos subtotais. Grupos, fundos e previsto do mês seguem a soma das
 * linhas, como na PO confirmada (Q29); divergência vira aviso. Nesta coluna o total impresso pode não incluir os
 * fundos (no piloto, 441.304,38 é a soma dos grupos 1.1 a 1.8): a conferência aceita as duas formas e diz qual valeu.
 */
public record ColunaImpressa(UUID poId, String rotulo, BigDecimal totalImpresso, boolean totalIncluiFundos,
        BigDecimal fundos, BigDecimal previstoMes, List<GrupoColuna> grupos, List<String> avisos) {

    /** Uma linha da coluna. {@code percentualTexto}: o "%" impresso na PO, como texto lido. */
    public record LinhaColuna(UUID linhaId, String codigo, String conta, String descricao, BigDecimal valor,
            String percentualTexto) {
    }

    /** Um grupo da coluna. {@code valor} = soma das linhas; {@code confere} = impresso e soma na tolerância. */
    public record GrupoColuna(UUID linhaId, String codigo, String descricao, boolean fundos, BigDecimal impresso,
            BigDecimal valor, BigDecimal diferenca, boolean confere, List<LinhaColuna> linhas) {
    }

    /** Diferença por grupo entre a PO anterior enviada e a coluna impressa (aviso, não achado). */
    public record DiferencaGrupo(String codigo, String descricao, BigDecimal poEnviada, BigDecimal colunaImpressa) {

        public String texto() {
            return "A coluna \"Orçado anterior\" difere da PO anterior enviada no grupo " + codigo + " " + descricao
                    + ": " + formatar(poEnviada) + " (PO enviada) × " + formatar(colunaImpressa) + " (coluna impressa)";
        }
    }

    /**
     * Monta a coluna "Orçado anterior" da PO. Vazio quando a PO não imprimiu a coluna (sem rótulo ou com todos os
     * valores zerados).
     */
    public static Optional<ColunaImpressa> de(Budget po, List<BudgetLine> linhas) {
        if (po.getPreviousBudgetedColumn() == null || po.getPreviousBudgetedColumn().isBlank()
                || linhas.stream().allMatch(l -> l.getPreviousBudgeted() == null || l.getPreviousBudgeted().signum() == 0)) {
            return Optional.empty();
        }
        BigDecimal tolerancia = po.getRoundingTolerance() == null ? new BigDecimal("0.01")
                : po.getRoundingTolerance();
        BudgetStructure estrutura = BudgetStructure.of(linhas);
        List<String> avisos = new ArrayList<>();
        List<GrupoColuna> grupos = new ArrayList<>();
        BigDecimal impressosComFundos = zero();
        BigDecimal impressosSemFundos = zero();
        BigDecimal previsto = zero();
        BigDecimal fundos = zero();
        for (BudgetStructure.Group g : estrutura.groups()) {
            List<LinhaColuna> doGrupo = g.lines().stream().map(ColunaImpressa::linha).toList();
            BigDecimal soma = doGrupo.stream().map(LinhaColuna::valor).reduce(zero(), BigDecimal::add);
            BigDecimal impresso = valor(g.line());
            BigDecimal diferenca = impresso.subtract(soma);
            boolean confere = diferenca.abs().compareTo(tolerancia) <= 0;
            grupos.add(new GrupoColuna(g.line().getId(), g.line().getEffectiveCode(), g.line().getDescription(),
                    g.funds(), impresso, soma, diferenca, confere, doGrupo));
            if (!confere) {
                avisos.add("Grupo " + g.name() + ": subtotal impresso " + formatar(impresso) + "; soma das linhas "
                        + formatar(soma) + " (diferença " + formatar(soma.subtract(impresso)) + "). Vale a soma das"
                        + " linhas.");
            }
            impressosComFundos = impressosComFundos.add(impresso);
            if (g.funds()) {
                fundos = fundos.add(soma);
            } else {
                impressosSemFundos = impressosSemFundos.add(impresso);
                previsto = previsto.add(soma);
            }
        }
        BigDecimal total = estrutura.total() == null ? null : valor(estrutura.total());
        boolean incluiFundos = true;
        if (total != null) {
            if (total.subtract(impressosComFundos).abs().compareTo(tolerancia) <= 0) {
                incluiFundos = true;
            } else if (estrutura.funds().isPresent()
                    && total.subtract(impressosSemFundos).abs().compareTo(tolerancia) <= 0) {
                incluiFundos = false;
                avisos.add("Nesta coluna o total impresso (" + formatar(total) + ") não inclui os fundos: confere com a"
                        + " soma dos subtotais sem os fundos (" + formatar(impressosSemFundos) + ").");
            } else {
                avisos.add("Total impresso " + formatar(total) + " não confere com a soma dos subtotais ("
                        + formatar(impressosComFundos) + " com os fundos; " + formatar(impressosSemFundos)
                        + " sem os fundos). Vale a soma das linhas.");
            }
        }
        return Optional.of(new ColunaImpressa(po.getId(), po.getPreviousBudgetedColumn().trim() + " (coluna impressa)",
                total, incluiFundos, fundos, previsto, List.copyOf(grupos), List.copyOf(avisos)));
    }

    /**
     * Diferenças acima da tolerância, por grupo, entre a PO anterior enviada (soma das linhas de cada grupo) e esta
     * coluna (RF-11.5). Grupos casados pelo código (1.1 a 1.9); grupo que só existe de um lado não é comparado.
     */
    public List<DiferencaGrupo> diferencas(List<BudgetLine> linhasDaPoAnterior, BigDecimal tolerancia) {
        Map<String, BigDecimal> enviada = new LinkedHashMap<>();
        for (BudgetStructure.Group g : BudgetStructure.of(linhasDaPoAnterior).groups()) {
            enviada.put(g.line().getEffectiveCode(), g.linesSum());
        }
        List<DiferencaGrupo> lista = new ArrayList<>();
        for (GrupoColuna g : grupos) {
            BigDecimal valor = enviada.get(g.codigo());
            if (valor != null && valor.subtract(g.valor()).abs().compareTo(tolerancia) > 0) {
                lista.add(new DiferencaGrupo(g.codigo(), g.descricao(), valor, g.valor()));
            }
        }
        return List.copyOf(lista);
    }

    /** Linha pelo código efetivo (o primeiro, se repetido). */
    public Optional<LinhaColuna> linha(String codigoEfetivo) {
        return grupos.stream().flatMap(g -> g.linhas().stream()).filter(l -> l.codigo().equals(codigoEfetivo))
                .findFirst();
    }

    private static LinhaColuna linha(BudgetLine l) {
        return new LinhaColuna(l.getId(), l.getEffectiveCode(), l.getAccount(), l.getDescription(), valor(l),
                l.getPercentageText());
    }

    private static BigDecimal valor(BudgetLine l) {
        return l.getPreviousBudgeted() == null ? zero() : l.getPreviousBudgeted();
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }
}
