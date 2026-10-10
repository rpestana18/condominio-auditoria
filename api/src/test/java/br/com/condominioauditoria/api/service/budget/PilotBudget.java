package br.com.condominioauditoria.api.service.budget;

import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetData;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetLineData;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetLineMark;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetLineType;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.TotalsCheckData;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.service.calculator.BudgetReadingAssessment;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Pilot budget 2026/2027, reduced: subtotals, total and the lines cited in RF-03.1.1 to RF-03.1.3 are the real ones;
 * the other lines of each group were summed into a single line ("demais"). As in the PDF, the 1.3 lines sum to
 * 336.274,18 (printed 336.274,17) and the 1.9 lines sum to 22.581,00 (printed 22.581,01). The checks mimic the rag's.
 */
public final class PilotBudget {

    private final List<BudgetLineData> lines = new ArrayList<>();
    private String staffSubtotal = "69193.86";
    private String reserveFund = null;
    private boolean gymEquipment = false;
    private boolean previousColumn = false;
    private String previousTotal = null;

    /**
     * "Orçado anterior" column (2025/2026) with the real PDF values in the groups and in the cited lines (RF-11.5):
     * total 441.304,38 without the funds, funds 22.065,22, 1.3.20 17.195,00. As in the PDF, group 1.6 prints 18.525,42
     * and leaves out line 1.6.21 (220,83). The other lines of each group were summed into the "demais" line.
     */
    private static final java.util.Map<String, String> PREVIOUS = java.util.Map.ofEntries(
            java.util.Map.entry("1|null", "441304.38"), java.util.Map.entry("1.1|null", "37661.43"),
            java.util.Map.entry("1.1.5|1553 - Férias", "339.45"), java.util.Map.entry("1.1.1|1500 - Demais",
                    "37321.98"),
            java.util.Map.entry("1.2|null", "565.00"), java.util.Map.entry("1.2.1|1560 - Consumo", "565.00"),
            java.util.Map.entry("1.3|null", "348631.55"), java.util.Map.entry("1.3.2|1598 - Bombas", "3200.00"),
            java.util.Map.entry("1.3.20|1682 - Sindicatura Profissional", "17195.00"),
            java.util.Map.entry("1.3.1|1692 - Demais", "328236.55"), java.util.Map.entry("1.5|null", "2350.00"),
            java.util.Map.entry("1.5.1|1700 - Bens", "2350.00"), java.util.Map.entry("1.6|null", "18525.42"),
            java.util.Map.entry("1.6.1|1710 - Demais", "18525.42"),
            java.util.Map.entry("1.6.21|1346 - Outros Serviços Contratados", "220.83"),
            java.util.Map.entry("1.7|null", "19300.00"), java.util.Map.entry("1.7.8|1606 - Material Hidráulico",
                    "19300.00"),
            java.util.Map.entry("1.8|null", "14270.99"), java.util.Map.entry("1.8.1|1693 - Serviços", "14270.99"),
            java.util.Map.entry("1.9|null", "22065.22"), java.util.Map.entry("1.9.1|null", "13239.13"),
            java.util.Map.entry("1.9.2|null", "8826.09"));

    public static PilotBudget defaults() {
        return new PilotBudget();
    }

    /** Test copy with a different printed Personnel subtotal (RF-03.1.2). */
    public PilotBudget withStaffSubtotal(String printed) {
        this.staffSubtotal = printed;
        return this;
    }

    /** Test copy with another reserve fund; the funds subtotal and the total follow (sums match). */
    public PilotBudget withReserveFund(String amount) {
        this.reserveFund = amount;
        return this;
    }

    /**
     * Test copy with account 1606 "Aparelhos de Ginástica" in 1.3.5 and 1.7.2, as in the real budget (RF-11.7), with
     * a zero amount so the sums still match.
     */
    public PilotBudget withGymEquipment() {
        this.gymEquipment = true;
        return this;
    }

    /** "Orçado anterior" column with the real PDF values (see {@link #PREVIOUS}). */
    public PilotBudget withPreviousColumn() {
        this.previousColumn = true;
        return this;
    }

    /** "Orçado anterior" column with another printed total (e.g. including the funds). */
    public PilotBudget withPreviousTotal(String total) {
        this.previousColumn = true;
        this.previousTotal = total;
        return this;
    }

    public BudgetData budget() {
        lines.clear();
        String reserve = reserveFund == null ? "13548.60" : reserveFund;
        String funds = reserveFund == null ? "22581.01" : new BigDecimal(reserveFund).add(new BigDecimal("9032.40")).toPlainString();
        String total = reserveFund == null ? "474201.13" : new BigDecimal("451620.12").add(new BigDecimal(funds)).toPlainString();
        add(BudgetLineType.TOTAL, "1", null, "Soma das seções 1.1 a 1.9", null, "TOTAL DAS DESPESAS", total, null);
        add(BudgetLineType.GROUP, "1.1", null, "Subtotal (soma linhas 5 a 18)", null, "PESSOAL", staffSubtotal, null);
        add(BudgetLineType.LINE, "1.1.5", "1553 - Férias", null, null, "Provisão de Férias", "1585.14", "366,97%");
        add(BudgetLineType.LINE, "1.1.1", "1500 - Demais", null, null, "Demais linhas de pessoal", "67608.72", null);
        add(BudgetLineType.GROUP, "1.2", null, "Subtotal (soma linhas 20 a 21)", null, "CONSUMO/UTILIDADES", "694.05",
                null);
        add(BudgetLineType.LINE, "1.2.1", "1560 - Consumo", null, null, "Consumo", "694.05", null);
        add(BudgetLineType.GROUP, "1.3", null, "Subtotal (soma linhas 23 a 46)", null, "SERVIÇOS - CONTRATOS EFETIVOS",
                "336274.17", null);
        add(BudgetLineType.LINE, "1.3.2", "1598 - Bombas", null, null, "Servirio Soluções Tecnicas Ltda", "3000.00",
                "-6,25%");
        if (gymEquipment) {
            add(BudgetLineType.LINE, "1.3.5", "1606 - Aparelhos de Ginástica", null, null, "Manutenção Academia",
                    "0.00", null);
        }
        add(BudgetLineType.LINE, "1.3.20", "1682 - Sindicatura Profissional", null, null, "Obm - Sergio Diniz",
                "8000.00", "-53,47%");
        add(BudgetLineType.LINE, "1.3.23", "1621 - Interfones", null, null,
                "Manutenção Preventiva De Interfones/Cftv", "0.00", null);
        add(BudgetLineType.LINE, "1.3.1", "1692 - Demais", null, null, "Demais contratos", "323755.25", null);
        add(BudgetLineType.LINE, "1.3.2", "1624 - Caixa D'água", null, null, "Caixa D'água", "1518.93", null);
        add(BudgetLineType.GROUP, "1.4", null, "Subtotal (soma linhas 49 a 51)", null, "TARIFAS PÚBLICAS", "0.00",
                null);
        add(BudgetLineType.LINE, "1.4.1", null, null, BudgetLineMark.SEPARATE_APPORTIONMENT, "Força e Luz", "0.00", null);
        add(BudgetLineType.LINE, "1.4.2", null, null, BudgetLineMark.SEPARATE_APPORTIONMENT, "Água e Esgoto", "0.00", null);
        add(BudgetLineType.LINE, "1.4.3", null, "Débito em receitas eventuais", BudgetLineMark.SEPARATE_APPORTIONMENT, "Gás",
                "0.00", null);
        add(BudgetLineType.GROUP, "1.5", null, "Subtotal (soma linhas 53 a 55)", null, "AQUISIÇÃO DE BENS", "2850.00",
                null);
        add(BudgetLineType.LINE, "1.5.1", "1700 - Bens", null, null, "Aquisição de bens", "2850.00", null);
        add(BudgetLineType.GROUP, "1.6", null, "Subtotal (soma linhas 57 a 77)", null, "DESPESAS ADMINISTRATIVAS",
                "17388.04", null);
        add(BudgetLineType.LINE, "1.6.15", null, null, BudgetLineMark.SEPARATE_APPORTIONMENT, "Seguro predial", "0.00", null);
        add(BudgetLineType.LINE, "1.6.1", "1710 - Demais", null, null, "Demais administrativas", "17388.04", null);
        if (previousColumn) {
            add(BudgetLineType.LINE, "1.6.21", "1346 - Outros Serviços Contratados", null, null,
                    "E-mail GoDaddy (anual)",
                    "0.00", "-32,07%");
        }
        add(BudgetLineType.GROUP, "1.7", null, "Subtotal (soma linhas 79 a 90)", null, "MATERIAIS/SUPRIMENTOS",
                "15200.00", null);
        if (gymEquipment) {
            add(BudgetLineType.LINE, "1.7.2", "1606 - Aparelhos de Ginástica", null, null, "Peças de Ginástica",
                    "0.00", null);
        }
        add(BudgetLineType.LINE, "1.7.8", "1606 - Material Hidráulico", null, null, "Material Hidráulico", "15200.00",
                null);
        add(BudgetLineType.GROUP, "1.8", null, "Subtotal (soma linhas 92 a 99)", null, "SERVIÇOS", "10020.00", null);
        add(BudgetLineType.LINE, "1.8.1", "1693 - Serviços", null, null, "Serviços", "10020.00", null);
        add(BudgetLineType.GROUP, "1.9", null, "Fundos", null, "Fundos do Condomínio", funds, null);
        add(BudgetLineType.LINE, "1.9.1", null, "Fundo de Reserva", null, "Fundo de Reserva", reserve, "3,00%");
        add(BudgetLineType.LINE, "1.9.2", null, "Obras Reformas e Infraestrutura", null, "Fundo de Obras", "9032.40",
                "2,00%");
        return new BudgetData("PROPOSTA ORÇAMENTÁRIA 2026 / 2027", "2026 / 2027", List.of("2025/2026", "2026/2027"),
                List.copyOf(lines));
    }

    /** As the rag checks (exact, to the cent): 1.3 and 1.9 fail by 0,01; 1.3.2 appears twice. */
    public List<TotalsCheckData> totalsChecks() {
        boolean staffOk = staffSubtotal.equals("69193.86");
        return List.of(
                new TotalsCheckData("GROUP_SUBTOTAL", "Soma das linhas do grupo 1.1 = subtotal impresso", staffOk,
                        "1.1 PESSOAL: soma das linhas 69.193,86; impresso " + staffSubtotal),
                passed("1.2"), failed("1.3", "336.274,18", "336.274,17"), passed("1.4"), passed("1.5"), passed("1.6"),
                        passed("1.7"),
                passed("1.8"), reserveFund == null ? failed("1.9", "22.581,00", "22.581,01") : passed("1.9"),
                new TotalsCheckData("TOTAL", "Soma dos grupos = total impresso", staffOk, "soma dos grupos x impresso"),
                new TotalsCheckData("MONTHLY_PLANNED", "Previsto do mês = total menos os fundos", staffOk,
                        "474.201,13 - 22.581,01 = 451.620,12"),
                new TotalsCheckData("FUND_RATE", "Cada fundo = taxa da coluna % sobre o previsto do mês", true,
                        "1.9.1: 3,00% de 451.620,12 = 13.548,60"),
                new TotalsCheckData("REPEATED_CODE", "Nenhum código de linha impresso mais de uma vez", false,
                        "1.3.2 aparece 2 vezes (ordens 8 e 12)"));
    }

    /** Lines as the api stores them, to test the pure functions without a database. */
    public List<BudgetLine> savedLines(Budget budget) {
        return budget().lines().stream().map(l -> BudgetImportService.line(budget, l)).toList();
    }

    public List<BudgetReadingAssessment.BudgetCheck> totalsChecksForAssessment() {
        return BudgetImportService.toAssessment(totalsChecks());
    }

    private static TotalsCheckData passed(String group) {
        return new TotalsCheckData("GROUP_SUBTOTAL", "Soma das linhas do grupo " + group + " = subtotal impresso", true,
                group + ": confere");
    }

    private static TotalsCheckData failed(String group, String sum, String printed) {
        return new TotalsCheckData("GROUP_SUBTOTAL", "Soma das linhas do grupo " + group + " = subtotal impresso",
                false,
                group + ": soma das linhas " + sum + "; impresso " + printed + "; diferença 0,01");
    }

    private void add(BudgetLineType type, String code, String account, String accountText, BudgetLineMark mark,
            String description,
            String budgeted, String percentage) {
        lines.add(new BudgetLineData(lines.size() + 1, 1, type, code, account, accountText, mark, description,
                new BigDecimal(previous(code, account)), new BigDecimal(budgeted), percentage, null));
    }

    private String previous(String code, String account) {
        if (!previousColumn) {
            return "0.00";
        }
        if (previousTotal != null && code.equals("1") && account == null) {
            return previousTotal;
        }
        return PREVIOUS.getOrDefault(code + "|" + account, "0.00");
    }
}
