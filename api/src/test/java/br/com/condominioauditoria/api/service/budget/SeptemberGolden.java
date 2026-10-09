package br.com.condominioauditoria.api.service.budget;

import br.com.condominioauditoria.api.dto.request.budget.AccountMappingBatchRequest;
import br.com.condominioauditoria.api.event.BudgetChanged;
import br.com.condominioauditoria.api.messaging.GoldenMessages;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage;
import br.com.condominioauditoria.api.model.accounting.Fund;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.budget.AccountMapping;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.AccountMappingBatchAction;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceptance case of RF-03.1.15 built from the private golden: budget 2026/2027 read and confirmed as the Admin would
 * (fiscal year 05/2026 to 04/2027, repeated 1.3.2 → 1.3.25, 1.9.1 → "FUNDO DE RESERVA", 1.9.2 → "OBRAS / REFORMAS /
 * INFRA") and the entries of the September/2026 cash flow, read from the v2 messages the rag publishes. Without the
 * golden, empty.
 */
public final class SeptemberGolden {

    public final BudgetScenario scenario;
    public final Budget budget;
    public final ProcessingResultMessage cashFlow;
    public final br.com.condominioauditoria.api.model.file.SourceFile file;
    public final UUID cashFlowFileId;
    /** Cash flow funds by printed name (the four of the scenario plus the others, created here). */
    public final Map<String, Fund> funds = new LinkedHashMap<>();

    private SeptemberGolden(BudgetScenario scenario, Budget budget, ProcessingResultMessage cashFlow) {
        this.scenario = scenario;
        this.budget = budget;
        this.cashFlow = cashFlow;
        for (Fund f : java.util.List.of(scenario.operatingFund, scenario.reserveFund, scenario.infraWorksFund,
                scenario.worksFund)) {
            funds.put(f.getName(), f);
        }
        var read = cashFlow.cashFlow();
        this.file = scenario.cashFlow("fluxo-caixa-2026-09.pdf", read.periodStart(), read.periodEnd(),
                read.entryCount());
        this.cashFlowFileId = file.getId();
        for (var section : read.sections()) {
            funds.computeIfAbsent(section.fund(), n -> {
                Fund created = new Fund(scenario.condominiumId, n);
                scenario.funds.add(created);
                return created;
            });
        }
        saveLedgerEntries();
    }

    /** Stores the cash flow entries as the ProcessingResultService does: each store creates entries with new ids. */
    private void saveLedgerEntries() {
        for (var section : cashFlow.cashFlow().sections()) {
            Fund f = funds.get(section.fund());
            section.entries().forEach(l -> scenario.ledgerEntries.add(new LedgerEntry(scenario.condominiumId,
                    cashFlowFileId,
                    f.getId(), l)));
        }
    }

    /**
     * Reprocessing of the same cash flow (SourceFileService.reprocess + ProcessingResultService): deletes the file's
     * entries and stores the same reading again, with new ids. Publishes the change, as the store does.
     */
    void reprocessCashFlow() {
        scenario.ledgerEntries.removeIf(l -> l.getFileId().equals(cashFlowFileId));
        saveLedgerEntries();
        scenario.published.add(BudgetChanged.of(scenario.condominiumId, "leitura do arquivo "
                + file.getOriginalName() + " gravada", "sistema", java.time.Instant.now()));
    }

    public static Optional<SeptemberGolden> load() {
        var readBudget = GoldenMessages.read(GoldenMessages.BUDGET_2026_2027);
        var cashFlow = GoldenMessages.read(GoldenMessages.SEPTEMBER_CASH_FLOW);
        if (readBudget.isEmpty() || cashFlow.isEmpty()) {
            return Optional.empty();
        }
        BudgetScenario scenario = new BudgetScenario();
        // The scenario calls the operating fund "CONDOMÍNIO", as in the pilot cash flow
        Budget budget = scenario.readBudget(readBudget.get().budget(), readBudget.get().totalsChecks());
        scenario.confirmation.confirm(scenario.condominiumId, budget.getId(), scenario.pilotRequest(budget), "admin");
        return Optional.of(new SeptemberGolden(scenario, budget, cashFlow.get()));
    }

    /** Pilot spreadsheet (mapa-contas-fluxo-para-PO.csv, copied to the private golden). */
    public static Optional<String> map() {
        return GoldenMessages.text("mapa-contas-fluxo-para-PO.csv");
    }

    /** Loads the map of the 73 accounts and confirms them all, as the Admin would with no change (RF-03.1.4). */
    void confirmMap() {
        String map = map().orElseThrow(() -> new IllegalStateException("mapa do piloto ausente no golden privado"));
        scenario.accountMapping.loadSheet(scenario.condominiumId, budget.getId(), "mapa-contas-fluxo-para-PO.csv", map,
                "admin");
        scenario.accountMapping.batch(scenario.condominiumId, budget.getId(),
                new AccountMappingBatchRequest(AccountMappingBatchAction.CONFIRMAR,
                scenario.mappings.stream().map(AccountMapping::getAccountCode).toList()), "admin");
    }

    BudgetVsActualCalculator.CashFlowFile septemberCashFlow() {
        return new BudgetVsActualCalculator.CashFlowFile(cashFlowFileId, file.getOriginalName(), file.getSha256(),
                file.getPeriodStart(), file.getPeriodEnd(), file.getUploadedAt(), file.getUploadedBy());
    }

    /** Period input of the pure function, with the Conv. 16.2 limit (20%). */
    BudgetVsActualCalculator.Input input(BudgetVsActualCalculator.Period period,
            java.util.List<BudgetVsActualCalculator.CashFlowFile> cashFlows,
            java.util.List<BudgetVsActualCalculator.ReallocatedEntry> reallocations) {
        Map<UUID, UUID> fundByLine = new LinkedHashMap<>();
        scenario.fundLinks.stream().filter(f -> f.getBudgetId().equals(budget.getId()))
                .forEach(f -> fundByLine.put(f.getBudgetLineId(), f.getFundId()));
        Map<UUID, String> names = new LinkedHashMap<>();
        funds.values().forEach(f -> names.put(f.getId(), f.getName()));
        return new BudgetVsActualCalculator.Input(budget, "PO-2026-2027-aprovada.pdf",
                scenario.lines.stream().filter(l -> l.getBudgetId().equals(budget.getId())).toList(),
                scenario.mappings.stream().filter(d -> d.getBudgetId().equals(budget.getId())).toList(), fundByLine,
                        names,
                scenario.operatingFund.getId(), cashFlows, scenario.ledgerEntries, reallocations,
                        new java.math.BigDecimal("20.0000"),
                java.util.List.of(), period);
    }

    BudgetVsActualCalculator.Calculation september() {
        return BudgetVsActualCalculator.calculate(input(new BudgetVsActualCalculator.Month(java.time.YearMonth.of(2026,
                9)),
                java.util.List.of(septemberCashFlow()), java.util.List.of()));
    }

    public BudgetLine line(String effectiveCode) {
        return scenario.lines.stream().filter(l -> l.getBudgetId().equals(budget.getId())
                && l.getEffectiveCode().equals(effectiveCode)).findFirst().orElseThrow();
    }
}
