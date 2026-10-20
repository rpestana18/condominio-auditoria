package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.Enrichment;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.FundPosition;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.LedgerEntryData;
import br.com.condominioauditoria.api.model.accounting.FundBalance;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.budget.AccountMapping;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.MappingTarget;
import br.com.condominioauditoria.api.model.enums.AccountMappingSource;
import br.com.condominioauditoria.api.model.enums.AccountMappingStatus;
import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import br.com.condominioauditoria.api.model.enums.BudgetLineType;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import br.com.condominioauditoria.api.model.enums.SpendingProfileStatus;
import br.com.condominioauditoria.api.service.calculator.BudgetTrackingGrid.LineProfile;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.CashFlowFile;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Cumulative;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Synthetic September/2026 (without the private golden) built so that the numbers of RF-13.4 and RF-13.6 come out:
 * planned 451.620,13; actual in lines 445.125,96; 25 lines above the planned amount summing 38.880,19; 1.1.1 actual
 * 26.377,53 (left over 9.556,62); 1.3.10 actual 86.816,34 (above 6.793,38); provisions 1.1.5 +1.585,14, 1.1.6
 * −2.265,84 and 1.1.12 +2.857,95 (2.177,25); savings in the lines 4.316,92; to reallocate 1.050,93 (1064); adjustments
 * 3.278,24 (1324 = 3.987,12 and 1327 = −708,88); fund CONDOMÍNIO closing 297.514,48; FUNDO DE RESERVA collected
 * 14.260,79 against 13.548,60. The other amounts (fee received, transfers, opening balances) are made up so the bridge
 * closes. Fiscal year 05/2026 to 04/2027.
 */
final class SyntheticSeptember {

    static final UUID CONDOMINIUM = UUID.randomUUID();
    static final UUID OPERATING = UUID.randomUUID();
    static final UUID RESERVE = UUID.randomUUID();
    static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    final Budget budget = confirmed();
    final List<BudgetLine> lines = new ArrayList<>();
    final List<AccountMapping> mappings = new ArrayList<>();
    final Map<String, BudgetLine> byCode = new LinkedHashMap<>();
    final List<LedgerEntry> entries = new ArrayList<>();
    final List<CashFlowFile> cashFlows = new ArrayList<>();
    final List<FundBalance> balances = new ArrayList<>();
    final Map<UUID, LocalDate> partialUntil = new HashMap<>();
    final UUID septemberFile = UUID.randomUUID();
    BudgetLine reserveLine;
    private int position;
    private int account = 2000;

    SyntheticSeptember() {
        add(BudgetLineType.TOTAL, "1", null, null, "TOTAL DAS DESPESAS", "0.00", null);
        add(BudgetLineType.GROUP, "1.1", null, null, "PESSOAL", "0.00", null);
        expense("1.1.1", "1545 - Salários", "Salários", "35934.15", "26377.53", null);
        expense("1.1.5", "1553 - Férias", "Provisão de Férias", "1585.14", "0.00", null);
        expense("1.1.6", "1554 - Rescisões", "Rescisões", "1000.00", "3265.84", null);
        expense("1.1.12", "1556 - 13º Salário", "13º Salário", "2857.95", "0.00", null);
        add(BudgetLineType.GROUP, "1.3", null, null, "SERVIÇOS - CONTRATOS EFETIVOS", "0.00", null);
        expense("1.3.1", "1692 - Demais", "Demais contratos", "267219.93", "267219.93", null);
        expense("1.3.10", "1442 - Vigia e Portaria", "Vigia e Portaria", "80022.96", "86816.34", null);
        // 23 more lines above the planned amount: 22 × 1.300,00 + 1.220,97 = 29.820,97
        for (int i = 0; i < 22; i++) {
            String name = "Contrato " + (char) ('A' + i);
            expense("1.3." + (30 + i), (1700 + i) + " - " + name, name, "1000.00", "2300.00", null);
        }
        expense("1.3.52", "1722 - Contrato W", "Contrato W", "1000.00", "2220.97", null);
        add(BudgetLineType.GROUP, "1.4", null, null, "TARIFAS PÚBLICAS", "0.00", null);
        BudgetLine light = add(BudgetLineType.LINE, "1.4.1", null, BudgetLineMark.SEPARATE_APPORTIONMENT,
                "Força e Luz", "0.00", null);
        byCode.put("1.4.1", light);
        add(BudgetLineType.GROUP, "1.6", null, null, "DESPESAS ADMINISTRATIVAS", "0.00", null);
        byCode.put("1.6.20", add(BudgetLineType.LINE, "1.6.20", "1347 - Assembleias", null, "Assembleias", "0.00",
                "média 3 assembléias no ano"));
        add(BudgetLineType.GROUP, "1.8", null, null, "SERVIÇOS", "0.00", null);
        expense("1.8.1", "1693 - Serviços", "Serviços", "40000.00", "8625.35", "meses nov à fev");
        expense("1.8.8", "1624 - Limpeza de caixa d'água", "Limpeza de caixa d'água", "0.00", "0.00", null);
        BudgetLine funds = new BudgetLine(budget, ++position, 1, BudgetLineType.GROUP, "1.9", null, "Fundos", null,
                "Fundos do Condomínio", BigDecimal.ZERO.setScale(2), new BigDecimal("13548.60"), null, null);
        lines.add(funds);
        reserveLine = add(BudgetLineType.LINE, "1.9.1", null, null, "Fundo de Reserva", "13548.60", null);

        // Outside the budget lines: adjustments and to reallocate
        special("1324", MappingTargetType.ADJUSTMENT, "3987.12");
        special("1327", MappingTargetType.ADJUSTMENT, "-708.88");
        special("1064", MappingTargetType.TO_REALLOCATE, "1050.93");
        LocalDate day = SEPTEMBER.atDay(10);
        // Operating fund credits and the transfer to the reserve fund
        entries.add(entry(septemberFile, OPERATING, "3001", "440000.00", "0.00", false, true, day));
        entries.add(entry(septemberFile, OPERATING, "3002", "1234.56", "0.00", false, false, day));
        entries.add(entry(septemberFile, OPERATING, "9999", "0.00", "5000.00", true, false, day));
        // Reserve fund: fee received, the transfer received and one expense of its own
        entries.add(entry(septemberFile, RESERVE, "3001", "14260.79", "0.00", false, true, day));
        entries.add(entry(septemberFile, RESERVE, "9999", "5000.00", "0.00", true, false, day));
        entries.add(entry(septemberFile, RESERVE, "4001", "0.00", "200.00", false, false, day));

        cashFlows.add(cashFlow(septemberFile, SEPTEMBER, SEPTEMBER.atEndOfMonth()));
        balances.add(balance(septemberFile, SEPTEMBER, OPERATING, "CONDOMÍNIO", "310735.05", "441234.56",
                "454455.13", "297514.48"));
        balances.add(balance(septemberFile, SEPTEMBER, RESERVE, "FUNDO DE RESERVA", "1000.00", "19260.79", "200.00",
                "20060.79"));
    }

    /** The suggested profiles, all confirmed (as in RF-13.6: "os tipos sugeridos confirmados"). */
    Map<UUID, LineProfile> confirmedProfiles() {
        Map<UUID, LineProfile> m = new HashMap<>();
        for (BudgetLine l : lines) {
            if (l.getType() == BudgetLineType.LINE && !l.getPrintedCode().startsWith("1.9")) {
                m.put(l.getId(), new LineProfile(SpendingProfileSuggestion.suggest(l).profile(),
                        SpendingProfileStatus.CONFIRMED));
            }
        }
        return m;
    }

    BudgetVsActualCalculator.Input base() {
        return new BudgetVsActualCalculator.Input(budget, "po.pdf", lines, mappings, Map.of(reserveLine.getId(),
                RESERVE), Map.of(OPERATING, "CONDOMÍNIO", RESERVE, "FUNDO DE RESERVA"), OPERATING, cashFlows, entries,
                List.of(), new BigDecimal("20.0000"), List.of(), new Cumulative());
    }

    BudgetTrackingGrid.Grid grid(Map<UUID, LineProfile> profiles) {
        return BudgetTrackingGrid.calculate(new BudgetTrackingGrid.Input(base(), partialUntil, profiles));
    }

    FundMovements.Input funds(YearMonth from, YearMonth to) {
        List<YearMonth> months = BudgetVsActualCalculator.months(from, to);
        Map<YearMonth, FundMovements.OperatingFundDebits> operating = new HashMap<>();
        Map<YearMonth, Map<UUID, BigDecimal>> planned = new HashMap<>();
        for (YearMonth m : months) {
            var r = BudgetVsActualCalculator.calculate(BudgetTrackingGrid.withPeriod(base(),
                    new BudgetVsActualCalculator.Month(m))).result();
            if (r.totals() == null) {
                continue;
            }
            operating.put(m, new FundMovements.OperatingFundDebits(r.totals().actualExpense(),
                    r.cashFlowCheck().transfers(), r.adjustments().total()));
            planned.put(m, FundMovements.planned(OPERATING, r.totals().monthlyPlanned(),
                    Map.of(RESERVE, reserveLine.getBudgeted())));
        }
        return new FundMovements.Input(months, cashFlows, partialUntil, balances, entries,
                Map.of(OPERATING, "CONDOMÍNIO", RESERVE, "FUNDO DE RESERVA"), OPERATING, operating, planned,
                Map.of(RESERVE, new FundMovements.FundLine(reserveLine.getId(), "1.9.1")));
    }

    BudgetLine line(String code) {
        return byCode.get(code);
    }

    /** Cash flow account mapped to the line. */
    String account(String code) {
        UUID id = line(code).getId();
        return mappings.stream().filter(m -> id.equals(m.getBudgetLineId())).findFirst().orElseThrow()
                .getAccountCode();
    }

    // ---- setup

    void expense(String code, String accountText, String description, String budgeted, String actual, String notes) {
        BudgetLine l = add(BudgetLineType.LINE, code, accountText, null, description, budgeted, notes);
        byCode.put(code, l);
        String accountCode = String.valueOf(++account);
        mappings.add(new AccountMapping(budget, accountCode, description.toUpperCase(), MappingTarget.line(l),
                AccountMappingStatus.CONFIRMED, AccountMappingSource.ADMIN, null, false, "admin", Instant.EPOCH));
        if (new BigDecimal(actual).signum() != 0) {
            entries.add(entry(septemberFile, OPERATING, accountCode, "0.00", actual, false, false,
                    SEPTEMBER.atDay(5)));
        }
    }

    void special(String accountCode, MappingTargetType type, String amount) {
        mappings.add(new AccountMapping(budget, accountCode, "CONTA " + accountCode, MappingTarget.special(type, null),
                AccountMappingStatus.CONFIRMED, AccountMappingSource.ADMIN, null, false, "admin", Instant.EPOCH));
        entries.add(entry(septemberFile, OPERATING, accountCode, "0.00", amount, false, false, SEPTEMBER.atDay(7)));
    }

    BudgetLine add(BudgetLineType type, String code, String accountText, BudgetLineMark mark, String description,
            String budgeted, String notes) {
        BudgetLine l = new BudgetLine(budget, ++position, 1, type, code, accountText, null, mark, description,
                BigDecimal.ZERO.setScale(2), new BigDecimal(budgeted), null, notes);
        lines.add(l);
        return l;
    }

    LedgerEntry entry(UUID file, UUID fund, String accountCode, String credit, String debit, boolean transfer,
            boolean fee, LocalDate date) {
        var read = new LedgerEntryData(1, entries.size() + 1, date, accountCode, "CONTA " + accountCode, "", "Teste",
                new BigDecimal(credit), new BigDecimal(debit), BigDecimal.ZERO.setScale(2),
                new Enrichment(null, null, null, transfer, fee));
        return new LedgerEntry(CONDOMINIUM, file, fund, read);
    }

    static CashFlowFile cashFlow(UUID file, YearMonth month, LocalDate end) {
        return new CashFlowFile(file, "fluxo-" + month + ".pdf", "f".repeat(64), month.atDay(1), end,
                Instant.EPOCH, "gestor");
    }

    static FundBalance balance(UUID file, YearMonth month, UUID fund, String name, String opening, String credits,
            String debits, String closing) {
        return new FundBalance(CONDOMINIUM, file, fund, month.atDay(1), month.atEndOfMonth(), new FundPosition(name,
                new BigDecimal(opening), new BigDecimal(credits), new BigDecimal(debits), new BigDecimal(closing)));
    }

    private static Budget confirmed() {
        Budget p = new Budget(CONDOMINIUM, UUID.randomUUID(), "a".repeat(64));
        p.recordReading("po-protest", "PO", null, null, null, BudgetStatus.READ, null, null, BigDecimal.ONE,
                new BigDecimal("0.01"), Instant.EPOCH);
        p.confirm(1, YearMonth.of(2026, 5), YearMonth.of(2027, 4), null, true, null, false, null, "admin",
                Instant.EPOCH);
        return p;
    }
}
