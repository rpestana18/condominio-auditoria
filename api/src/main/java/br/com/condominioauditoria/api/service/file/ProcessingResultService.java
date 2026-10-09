package br.com.condominioauditoria.api.service.file;

import br.com.condominioauditoria.api.messaging.ProcessingResultMessage;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetData;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.CashFlow;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.FundPosition;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.Section;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.TotalsCheckData;
import br.com.condominioauditoria.api.model.accounting.Fund;
import br.com.condominioauditoria.api.model.accounting.FundBalance;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.accounting.TotalsCheck;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.FileStatus;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.orcamento.EstadoPrevisao;
import br.com.condominioauditoria.api.orcamento.GravacaoPrevisao;
import br.com.condominioauditoria.api.orcamento.MudancaOrcamento;
import br.com.condominioauditoria.api.orcamento.PrevisaoOrcamentaria;
import br.com.condominioauditoria.api.repository.accounting.FundBalanceRepository;
import br.com.condominioauditoria.api.repository.accounting.FundRepository;
import br.com.condominioauditoria.api.repository.accounting.LedgerEntryRepository;
import br.com.condominioauditoria.api.repository.accounting.TotalsCheckRepository;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves everything the rag extracted from a file in a single transaction: either all of it goes in, or nothing
 * (rollback). Before inserting, deletes the previous extraction of the same file: reprocessing never duplicates an
 * entry or a budget. The budget read is only saved if the file is in the PO category; an already confirmed budget is
 * not replaced by a new read.
 */
@Service
public class ProcessingResultService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingResultService.class);

    private final SourceFileRepository files;
    private final FundRepository funds;
    private final LedgerEntryRepository entries;
    private final FundBalanceRepository balances;
    private final TotalsCheckRepository totalsChecks;
    private final GravacaoPrevisao budgets;
    private final ApplicationEventPublisher events;

    ProcessingResultService(SourceFileRepository files, FundRepository funds, LedgerEntryRepository entries,
            FundBalanceRepository balances, TotalsCheckRepository totalsChecks, GravacaoPrevisao budgets,
            ApplicationEventPublisher publisher) {
        this.files = files;
        this.funds = funds;
        this.entries = entries;
        this.balances = balances;
        this.totalsChecks = totalsChecks;
        this.budgets = budgets;
        this.events = publisher;
    }

    @Transactional
    public void save(ProcessingResultMessage result) {
        SourceFile file = files.findById(result.fileId()).orElse(null);
        if (file == null || !file.isCurrentProcessing(result.processingId())) {
            log.info("Resultado descartado: arquivo {} foi apagado ou reprocessado depois desta leitura",
                    result.fileId());
            return;
        }
        Optional<PrevisaoOrcamentaria> locked = budgets.travada(file);
        if (locked.isPresent()) {
            // ADR 0004, Decision 3: numbers of a confirmed budget may already have been exported; nothing from this
            // read goes in
            file.complete(FileStatus.CONCLUIDO, "A PO deste arquivo já foi confirmada e não foi alterada. "
                    + "Para mudar a PO, envie o arquivo corrigido e confirme como nova versão.",
                    locked.get().getInterpretador(), null, null, null);
            return;
        }
        entries.deleteByFileId(file.getId());
        balances.deleteByFileId(file.getId());
        totalsChecks.deleteByFileId(file.getId());
        // Cash flow saved (or deleted, if the category changed): the budget findings are recalculated after the commit
        events.publishEvent(MudancaOrcamento.de(file.getCondominiumId(), "leitura do arquivo "
                + file.getOriginalName() + " gravada", "sistema", Instant.now()));

        BudgetData budgetData = result.budget();
        if (budgetData == null || file.getCategory() != FileCategory.PO) {
            budgets.removerNaoConfirmada(file);
        }
        if (budgetData != null) {
            saveBudget(file, result, budgetData);
            return;
        }

        CashFlow cashFlow = result.cashFlow();
        if (cashFlow != null && file.getCategory() != FileCategory.BALANCETE) {
            // RF-01.7: only the trial balance and cash flow category creates entries, balances and totals checks.
            file.complete(FileStatus.CONCLUIDO,
                    "Arquivo guardado. Ele parece um fluxo de caixa: para gerar os lançamentos, mude a categoria para \""
                            + FileCategory.BALANCETE.label() + "\".",
                    null, null, null, null);
            return;
        }
        if (cashFlow == null) {
            file.complete(FileStatus.CONCLUIDO,
                    "Arquivo guardado. A leitura dos dados deste tipo de documento ainda vai ser construída.",
                    null, null, null, null);
            return;
        }
        List<TotalsCheckData> checks = result.totalsChecks() == null ? List.of() : result.totalsChecks();
        saveCashFlow(file, cashFlow, checks);
        long failures = checks.stream().filter(v -> !v.ok()).count();
        file.complete(failures == 0 ? FileStatus.CONCLUIDO : FileStatus.PRECISA_REVISAO,
                failures == 0 ? "Todas as conferências passaram" : failures + " conferência(s) não bateram",
                result.parser(), cashFlow.periodStart(), cashFlow.periodEnd(), cashFlow.entryCount());
        log.info("Arquivo {} gravado: {} lançamentos", file.getOriginalName(), cashFlow.entryCount());
    }

    private void saveBudget(SourceFile file, ProcessingResultMessage result, BudgetData budgetData) {
        if (file.getCategory() != FileCategory.PO) {
            // Same RF-01.7 rule as for the cash flow: only the PO category saves the budget
            file.complete(FileStatus.CONCLUIDO,
                    "Arquivo guardado. Ele parece uma previsão orçamentária: para gravar a PO, mude a categoria para \""
                            + FileCategory.PO.label() + "\".",
                    null, null, null, null);
            return;
        }
        List<TotalsCheckData> checks = result.totalsChecks() == null ? List.of() : result.totalsChecks();
        for (int i = 0; i < checks.size(); i++) {
            totalsChecks.save(new TotalsCheck(file.getId(), i + 1, checks.get(i)));
        }
        PrevisaoOrcamentaria budget = budgets.gravar(file, result.parser(), budgetData, checks);
        boolean divergent = budget.getEstado() == EstadoPrevisao.LIDA_COM_DIVERGENCIA;
        file.complete(divergent ? FileStatus.PRECISA_REVISAO : FileStatus.CONCLUIDO,
                divergent
                        ? "PO lida com divergência: confira as somas antes de confirmar"
                        : "PO lida: aguarda a confirmação do Admin",
                result.parser(), null, null, null);
        log.info("PO do arquivo {} gravada: {} linhas, estado {}", file.getOriginalName(), budgetData.lines().size(),
                budget.getEstado());
    }

    private void saveCashFlow(SourceFile file, CashFlow cashFlow, List<TotalsCheckData> checks) {
        UUID condominiumId = file.getCondominiumId();
        Map<String, UUID> fundByName = new HashMap<>();
        List<LedgerEntry> newEntries = new ArrayList<>();
        for (Section section : cashFlow.sections()) {
            UUID fundId = fundByName.computeIfAbsent(section.fund(), name -> fundId(condominiumId, name));
            section.entries().forEach(l -> newEntries.add(new LedgerEntry(condominiumId, file.getId(), fundId, l)));
        }
        entries.saveAll(newEntries);

        List<FundBalance> positions = new ArrayList<>();
        for (FundPosition p : cashFlow.financialPosition()) {
            UUID fundId = fundByName.computeIfAbsent(p.fund(), name -> fundId(condominiumId, name));
            positions.add(new FundBalance(condominiumId, file.getId(), fundId, cashFlow.periodStart(), cashFlow.periodEnd(), p));
        }
        balances.saveAll(positions);

        for (int i = 0; i < checks.size(); i++) {
            totalsChecks.save(new TotalsCheck(file.getId(), i + 1, checks.get(i)));
        }
    }

    private UUID fundId(UUID condominiumId, String name) {
        return funds.findByCondominiumIdAndName(condominiumId, name)
                .orElseGet(() -> funds.save(new Fund(condominiumId, name)))
                .getId();
    }
}
