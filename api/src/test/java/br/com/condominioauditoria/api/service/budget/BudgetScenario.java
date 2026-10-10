package br.com.condominioauditoria.api.service.budget;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.config.properties.AccountMappingProperties;
import br.com.condominioauditoria.api.config.properties.BudgetProperties;
import br.com.condominioauditoria.api.dto.request.budget.BudgetConfirmationRequest;
import br.com.condominioauditoria.api.dto.request.budget.EffectiveCodeRequest;
import br.com.condominioauditoria.api.dto.request.budget.FundLinkRequest;
import br.com.condominioauditoria.api.event.BudgetChanged;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetData;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.Enrichment;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.LedgerEntryData;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.TotalsCheckData;
import br.com.condominioauditoria.api.model.accounting.Fund;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.accounting.TotalsCheck;
import br.com.condominioauditoria.api.model.audit.Finding;
import br.com.condominioauditoria.api.model.audit.FindingEvent;
import br.com.condominioauditoria.api.model.audit.FindingEvidence;
import br.com.condominioauditoria.api.model.audit.RuleParameter;
import br.com.condominioauditoria.api.model.budget.AccountMapping;
import br.com.condominioauditoria.api.model.budget.AccountMappingEvent;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetEvent;
import br.com.condominioauditoria.api.model.budget.BudgetFundLink;
import br.com.condominioauditoria.api.model.budget.BudgetItem;
import br.com.condominioauditoria.api.model.budget.BudgetItemEvent;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.BudgetLineItem;
import br.com.condominioauditoria.api.model.budget.Reallocation;
import br.com.condominioauditoria.api.model.budget.ReallocationEvent;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.FileStatus;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.accounting.FundRepository;
import br.com.condominioauditoria.api.repository.accounting.LedgerEntryRepository;
import br.com.condominioauditoria.api.repository.accounting.TotalsCheckRepository;
import br.com.condominioauditoria.api.repository.audit.FindingEventRepository;
import br.com.condominioauditoria.api.repository.audit.FindingEvidenceRepository;
import br.com.condominioauditoria.api.repository.audit.FindingRepository;
import br.com.condominioauditoria.api.repository.audit.RuleParameterRepository;
import br.com.condominioauditoria.api.repository.budget.AccountMappingEventRepository;
import br.com.condominioauditoria.api.repository.budget.AccountMappingRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetEventRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetFundLinkRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetItemEventRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetItemRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineItemRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.repository.budget.ReallocationEventRepository;
import br.com.condominioauditoria.api.repository.budget.ReallocationRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.service.audit.BudgetFindingRecalculationService;
import br.com.condominioauditoria.api.service.audit.FindingSyncService;
import br.com.condominioauditoria.api.service.audit.rule.MonthlyOverrunRule;
import br.com.condominioauditoria.api.service.audit.rule.ReserveFundCapRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory repositories to test the budget confirmation without a database. */
public final class BudgetScenario {

    public final UUID condominiumId = UUID.randomUUID();
    public final Condominium condominium = mock(Condominium.class);
    public final Fund operatingFund = new Fund(condominiumId, "CONDOMÍNIO");
    public final Fund reserveFund = new Fund(condominiumId, "FUNDO DE RESERVA");
    public final Fund infraWorksFund = new Fund(condominiumId, "OBRAS / REFORMAS / INFRA");
    public final Fund worksFund = new Fund(condominiumId, "OBRAS");
    public final SourceFile minutes;

    public final Map<UUID, SourceFile> files = new LinkedHashMap<>();
    public final Map<UUID, Budget> budgets = new LinkedHashMap<>();
    public final List<BudgetLine> lines = new ArrayList<>();
    public final List<TotalsCheck> totalsChecks = new ArrayList<>();
    public final List<BudgetFundLink> fundLinks = new ArrayList<>();
    public final List<BudgetEvent> budgetEvents = new ArrayList<>();
    public final List<Finding> findings = new ArrayList<>();
    public final List<FindingEvidence> evidence = new ArrayList<>();
    public final List<RuleParameter> ruleParameters = new ArrayList<>();
    public final List<AccountMapping> mappings = new ArrayList<>();
    public final List<AccountMappingEvent> mappingEvents = new ArrayList<>();
    public final List<LedgerEntry> ledgerEntries = new ArrayList<>();
    public final List<FindingEvent> findingEvents = new ArrayList<>();
    public final List<Reallocation> reallocations = new ArrayList<>();
    public final List<ReallocationEvent> reallocationEvents = new ArrayList<>();
    public final List<Fund> funds = new ArrayList<>();
    public final List<BudgetItem> budgetItems = new ArrayList<>();
    public final List<BudgetLineItem> lineItems = new ArrayList<>();
    public final List<BudgetItemEvent> budgetItemEvents = new ArrayList<>();
    /** Changes published by the services; {@link #afterCommit()} plays the after-commit trigger. */
    public final List<Object> published = new ArrayList<>();

    public final BudgetConfirmationService confirmation;
    public final BudgetQueryService query;
    public final AccountMappingService accountMapping;
    public final BudgetItemService budgetItemService;
    public final BudgetExtensionService extension;
    public final FiscalYearService fiscalYears;
    public final FiscalYearComparisonService comparison;
    public final IndicatorService indicators;
    public final BudgetRepository budgetRepo;
    public final BudgetLineRepository lineRepo;
    public final AccountMappingRepository mappingRepo;
    public final AccountMappingEventRepository mappingEventRepo;
    public final BudgetVsActualQueryService budgetVsActual;
    public final ReallocationService reallocation;
    public final BudgetFindingRecalculationService recalculation;
    public final FindingSyncService findingSync;
    public final BudgetFundLinkService fundLinkService;
    private final BudgetImportService budgetImport;

    public BudgetScenario() {
        when(condominium.getId()).thenReturn(condominiumId);
        when(condominium.getOperatingFundId()).thenReturn(operatingFund.getId());
        CondominiumRepository condominiums = mock(CondominiumRepository.class);
        when(condominiums.lockById(condominiumId)).thenReturn(Optional.of(condominium));
        when(condominiums.findById(condominiumId)).thenReturn(Optional.of(condominium));

        SourceFileRepository fileRepo = mock(SourceFileRepository.class);
        when(fileRepo.findById(any())).thenAnswer(i -> Optional.ofNullable(files.get(i.<UUID>getArgument(0))));
        when(fileRepo.findByIdAndCondominiumId(any(), any())).thenAnswer(i -> Optional
                .ofNullable(files.get(i.<UUID>getArgument(0))).filter(a -> a.getCondominiumId().equals(i.getArgument(1))));

        budgetRepo = mock(BudgetRepository.class);
        when(budgetRepo.save(any())).thenAnswer(i -> {
            Budget p = i.getArgument(0);
            budgets.put(p.getId(), p);
            return p;
        });
        when(budgetRepo.findByFileId(any())).thenAnswer(i -> budgets.values().stream()
                .filter(p -> p.getFileId().equals(i.getArgument(0))).findFirst());
        when(budgetRepo.findByIdAndCondominiumId(any(), any())).thenAnswer(i -> Optional
                .ofNullable(budgets.get(i.<UUID>getArgument(0))).filter(p -> p.getCondominiumId().equals(i.getArgument(1))));
        when(budgetRepo.findByCondominiumIdAndStatusIn(any(), any())).thenAnswer(i -> budgets.values().stream()
                .filter(p -> p.getCondominiumId().equals(i.getArgument(0))
                        && i.<Collection<BudgetStatus>>getArgument(1).contains(p.getStatus()))
                .toList());

        lineRepo = mock(BudgetLineRepository.class);
        when(lineRepo.saveAll(any())).thenAnswer(i -> {
            for (BudgetLine l : i.<Iterable<BudgetLine>>getArgument(0)) {
                if (!lines.contains(l)) {
                    lines.add(l);
                }
            }
            return i.getArgument(0);
        });
        when(lineRepo.findByBudgetIdOrderByPosition(any())).thenAnswer(i -> lines.stream()
                .filter(l -> l.getBudgetId().equals(i.getArgument(0))).sorted(Comparator.comparingInt(BudgetLine::getPosition))
                .toList());

        TotalsCheckRepository totalsCheckRepo = mock(TotalsCheckRepository.class);
        when(totalsCheckRepo.findByFileIdOrderBySequence(any())).thenAnswer(i -> totalsChecks.stream()
                .filter(c -> c.getFileId().equals(i.getArgument(0))).toList());

        FundRepository fundRepo = mock(FundRepository.class);
        funds.addAll(List.of(operatingFund, reserveFund, infraWorksFund, worksFund));
        when(fundRepo.findByCondominiumId(condominiumId)).thenAnswer(i -> List.copyOf(funds));
        when(fileRepo.findByCondominiumIdAndCategoryAndStatusIn(any(), any(), any())).thenAnswer(i -> files
                .values().stream().filter(a -> a.getCondominiumId().equals(i.getArgument(0))
                        && a.getCategory() == i.getArgument(1)
                        && i.<Collection<FileStatus>>getArgument(2).contains(a.getStatus()))
                .toList());

        BudgetFundLinkRepository fundLinkRepo = mock(BudgetFundLinkRepository.class);
        when(fundLinkRepo.save(any())).thenAnswer(i -> {
            fundLinks.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(fundLinkRepo.findByBudgetId(any())).thenAnswer(i -> fundLinks.stream()
                .filter(f -> f.getBudgetId().equals(i.getArgument(0))).toList());
        org.mockito.Mockito.doAnswer(i -> fundLinks.removeIf(f -> f.getBudgetId().equals(i.getArgument(0))))
                .when(fundLinkRepo).deleteByBudgetId(any());

        BudgetEventRepository eventRepo = mock(BudgetEventRepository.class);
        when(eventRepo.save(any())).thenAnswer(i -> {
            budgetEvents.add(i.getArgument(0));
            return i.getArgument(0);
        });

        FindingRepository findingRepo = mock(FindingRepository.class);
        when(findingRepo.save(any())).thenAnswer(i -> {
            if (!findings.contains(i.<Finding>getArgument(0))) {
                findings.add(i.getArgument(0));
            }
            return i.getArgument(0);
        });
        when(findingRepo.findByCondominiumIdAndReferenceMonthAndRuleIn(any(), any(), any())).thenAnswer(i -> findings
                .stream().filter(a -> a.getCondominiumId().equals(i.getArgument(0))
                        && a.getReferenceMonth().atDay(1).equals(i.getArgument(1))
                        && i.<Collection<String>>getArgument(2).contains(a.getRule()))
                .toList());
        FindingEventRepository findingEventRepo = mock(FindingEventRepository.class);
        when(findingEventRepo.save(any())).thenAnswer(i -> {
            findingEvents.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(findingRepo.findByCondominiumIdAndRuleAndReferenceMonthAndTarget(any(), anyString(), any(), anyString()))
                .thenAnswer(i -> findings.stream().filter(a -> a.getCondominiumId().equals(i.getArgument(0))
                        && a.getRule().equals(i.getArgument(1))
                        && a.getReferenceMonth().atDay(1).equals(i.getArgument(2)) && a.getTarget().equals(i.getArgument(3)))
                        .findFirst());
        when(findingRepo.findByCondominiumIdAndTargetStartingWithOrderByCreatedAt(any(),
                anyString())).thenAnswer(i -> findings
                .stream().filter(a -> a.getCondominiumId().equals(i.getArgument(0))
                        && a.getTarget().startsWith(i.getArgument(1))).toList());
        when(findingRepo.findByCondominiumIdOrderByReferenceMonthDescCreatedAtAsc(any())).thenAnswer(i -> findings.stream()
                .filter(a -> a.getCondominiumId().equals(i.getArgument(0))).toList());
        FindingEvidenceRepository evidenceRepo = mock(FindingEvidenceRepository.class);
        when(evidenceRepo.save(any())).thenAnswer(i -> {
            evidence.add(i.getArgument(0));
            return i.getArgument(0);
        });

        RuleParameterRepository parameterRepo = mock(RuleParameterRepository.class);
        when(parameterRepo.findValidOn(any(), anyString(), any())).thenAnswer(i -> ruleParameters.stream()
                .filter(p -> p.getCode().equals(i.getArgument(1))
                        && !p.getValidFrom().isAfter(i.getArgument(2)))
                .findFirst());
        ruleParameters.add(new RuleParameter(condominiumId, ReserveFundCapRule.PARAMETER, new BigDecimal("5.0000"),
                LocalDate.of(1900, 1, 1), null, "Conv. 20.1"));
        ruleParameters.add(new RuleParameter(condominiumId, MonthlyOverrunRule.PARAMETER, new BigDecimal("20.0000"),
                LocalDate.of(1900, 1, 1), null, "Conv. 16.2"));

        BudgetReserveFundService budgetReserve = new BudgetReserveFundService(parameterRepo);
        query = new BudgetQueryService(budgetRepo, lineRepo, totalsCheckRepo, fileRepo, fundLinkRepo, fundRepo,
                findingRepo, budgetReserve, eventRepo);
        findingSync = new FindingSyncService(findingRepo, evidenceRepo, findingEventRepo);
        BudgetItemRepository itemRepo = mock(BudgetItemRepository.class);
        when(itemRepo.save(any())).thenAnswer(i -> {
            if (!budgetItems.contains(i.<BudgetItem>getArgument(0))) {
                budgetItems.add(i.getArgument(0));
            }
            return i.getArgument(0);
        });
        when(itemRepo.existsByCondominiumId(any())).thenAnswer(i -> budgetItems.stream()
                .anyMatch(r -> r.getCondominiumId().equals(i.getArgument(0))));
        when(itemRepo.findByCondominiumIdOrderByName(any())).thenAnswer(i -> budgetItems.stream()
                .filter(r -> r.getCondominiumId().equals(i.getArgument(0))).sorted(Comparator.comparing(BudgetItem::getName))
                .toList());
        when(itemRepo.findById(any())).thenAnswer(i -> budgetItems.stream()
                .filter(r -> r.getId().equals(i.getArgument(0))).findFirst());
        when(itemRepo.findByIdAndCondominiumId(any(), any())).thenAnswer(i -> budgetItems.stream()
                .filter(r -> r.getId().equals(i.getArgument(0)) && r.getCondominiumId().equals(i.getArgument(1)))
                .findFirst());
        BudgetLineItemRepository lineItemRepo = mock(BudgetLineItemRepository.class);
        when(lineItemRepo.save(any())).thenAnswer(i -> {
            if (!lineItems.contains(i.<BudgetLineItem>getArgument(0))) {
                lineItems.add(i.getArgument(0));
            }
            return i.getArgument(0);
        });
        when(lineItemRepo.findByBudgetId(any())).thenAnswer(i -> lineItems.stream()
                .filter(l -> l.getBudgetId().equals(i.getArgument(0))).toList());
        when(lineItemRepo.findByCondominiumIdAndStatus(any(), any())).thenAnswer(i -> lineItems.stream()
                .filter(l -> l.getCondominiumId().equals(i.getArgument(0)) && l.getStatus() == i.getArgument(1))
                .toList());
        BudgetItemEventRepository itemEventRepo = mock(BudgetItemEventRepository.class);
        when(itemEventRepo.save(any())).thenAnswer(i -> {
            budgetItemEvents.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(itemEventRepo.findByBudgetIdOrderByOccurredAtAscLineCodeAsc(any())).thenAnswer(i -> budgetItemEvents.stream()
                .filter(e -> i.getArgument(0).equals(e.getBudgetId())).toList());
        budgetItemService = new BudgetItemService(condominiums, budgetRepo, lineRepo, itemRepo, lineItemRepo,
                itemEventRepo);
        confirmation = new BudgetConfirmationService(condominiums, budgetRepo, lineRepo, fundLinkRepo, eventRepo,
                fileRepo, fundRepo, query, budgetReserve, findingSync, budgetItemService, published::add);
        budgetImport = new BudgetImportService(budgetRepo, lineRepo, new BudgetProperties(new BigDecimal("0.01")));

        mappingRepo = mock(AccountMappingRepository.class);
        when(mappingRepo.save(any())).thenAnswer(i -> {
            AccountMapping d = i.getArgument(0);
            if (!mappings.contains(d)) {
                mappings.add(d);
            }
            return d;
        });
        when(mappingRepo.findByBudgetIdOrderByAccountCode(any())).thenAnswer(i -> mappings.stream()
                .filter(d -> d.getBudgetId().equals(i.getArgument(0)))
                .sorted(Comparator.comparing(AccountMapping::getAccountCode)).toList());
        when(mappingRepo.findByBudgetIdAndAccountCode(any(), anyString())).thenAnswer(i -> mappings.stream()
                .filter(d -> d.getBudgetId().equals(i.getArgument(0)) && d.getAccountCode().equals(i.getArgument(1)))
                .findFirst());
        mappingEventRepo = mock(AccountMappingEventRepository.class);
        when(mappingEventRepo.save(any())).thenAnswer(i -> {
            mappingEvents.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(mappingEventRepo.findByBudgetIdOrderByOccurredAtAscAccountCodeAsc(any())).thenAnswer(i -> mappingEvents.stream()
                .filter(e -> e.getBudgetId().equals(i.getArgument(0))).toList());
        LedgerEntryRepository entryRepo = mock(LedgerEntryRepository.class);
        when(entryRepo.debitsWithAccount(any(), any(), any(), any())).thenAnswer(i -> ledgerEntries.stream()
                .filter(l -> l.getFundId().equals(i.getArgument(1)) && !l.getDate().isBefore(i.getArgument(2))
                        && !l.getDate().isAfter(i.getArgument(3)) && l.getDebit().signum() != 0
                        && !l.isInterFundTransfer() && l.getAccountCode() != null)
                .sorted(Comparator.comparing(LedgerEntry::getDate)).toList());
        when(entryRepo.findByFileIdInAndDateBetween(any(), any(), any())).thenAnswer(i -> ledgerEntries.stream()
                .filter(l -> i.<Collection<UUID>>getArgument(0).contains(l.getFileId())
                        && !l.getDate().isBefore(i.getArgument(1)) && !l.getDate().isAfter(i.getArgument(2)))
                .toList());
        when(entryRepo.findById(any())).thenAnswer(i -> ledgerEntries.stream()
                .filter(l -> l.getId().equals(i.getArgument(0))).findFirst());
        accountMapping = new AccountMappingService(condominiums, budgetRepo, lineRepo, mappingRepo, mappingEventRepo,
                entryRepo,
                AccountMappingProperties.defaults(), published::add);

        ReallocationRepository reallocationRepo = mock(ReallocationRepository.class);
        when(reallocationRepo.save(any())).thenAnswer(i -> {
            if (!reallocations.contains(i.<Reallocation>getArgument(0))) {
                reallocations.add(i.getArgument(0));
            }
            return i.getArgument(0);
        });
        when(reallocationRepo.findByBudgetIdAndUndoneAtIsNull(any())).thenAnswer(i -> reallocations.stream()
                .filter(r -> r.getBudgetId().equals(i.getArgument(0)) && r.active()).toList());
        when(reallocationRepo.findByBudgetIdOrderByDateAscReallocatedAtAsc(any())).thenAnswer(i -> reallocations.stream()
                .filter(r -> r.getBudgetId().equals(i.getArgument(0))).toList());
        when(reallocationRepo.findByBudgetIdAndEntryKeyAndUndoneAtIsNull(any(), anyString()))
                .thenAnswer(i -> reallocations.stream().filter(r -> r.getBudgetId().equals(i.getArgument(0))
                        && r.getEntryKey().equals(i.getArgument(1)) && r.active()).findFirst());
        when(reallocationRepo.findByIdAndCondominiumId(any(), any())).thenAnswer(i -> reallocations.stream()
                .filter(r -> r.getId().equals(i.getArgument(0)) && r.getCondominiumId().equals(i.getArgument(1)))
                .findFirst());
        ReallocationEventRepository reallocationEventRepo = mock(ReallocationEventRepository.class);
        when(reallocationEventRepo.save(any())).thenAnswer(i -> {
            reallocationEvents.add(i.getArgument(0));
            return i.getArgument(0);
        });
        budgetVsActual = new BudgetVsActualQueryService(condominiums, budgetRepo, lineRepo, mappingRepo,
                fundLinkRepo, fundRepo, fileRepo, entryRepo, parameterRepo, query, reallocationRepo);
        reallocation = new ReallocationService(condominiums, entryRepo, fileRepo, query, budgetRepo,
                lineRepo, mappingRepo, reallocationRepo, reallocationEventRepo, published::add);
        recalculation = new BudgetFindingRecalculationService(condominiums, budgetRepo, budgetVsActual, findingSync);
        extension = new BudgetExtensionService(condominiums, budgetRepo, eventRepo, query, published::add);
        fiscalYears = new FiscalYearService(budgetRepo, lineRepo, budgetVsActual, accountMapping, budgetItemService);
        comparison = new FiscalYearComparisonService(condominiums, budgetRepo, lineRepo, fundLinkRepo, itemRepo,
                lineItemRepo, findingRepo, budgetVsActual, fiscalYears);
        indicators = new IndicatorService(condominiums, budgetRepo, budgetVsActual, fiscalYears, comparison);
        fundLinkService = new BudgetFundLinkService(condominiums, budgetRepo, lineRepo, fundLinkRepo, fundRepo,
                eventRepo,
                query, published::add);

        minutes = file(FileCategory.MINUTES, "ata-ago-2026-05.pdf");
    }

    /**
     * Plays the {@link BudgetFindingRecalculationListener}: for each change published since the last call,
     * recalculates the findings (as after the commit). Returns how many changes were handled.
     */
    public int afterCommit() {
        List<BudgetChanged> changes = published.stream().filter(BudgetChanged.class::isInstance)
                .map(BudgetChanged.class::cast).toList();
        published.clear();
        changes.forEach(recalculation::recalculate);
        return changes.size();
    }

    /** Cash flow file of the trial balances category, read (completed), covering the period. */
    public SourceFile cashFlow(String name, LocalDate start, LocalDate end, int entries) {
        SourceFile a = file(FileCategory.TRIAL_BALANCE, name);
        a.complete(FileStatus.COMPLETED, "Todas as conferências passaram", "fluxo-protest", start, end, entries);
        return a;
    }

    /** Reads the budget as the ProcessingResultService would (lines and checks stored). */
    public Budget readBudget(PilotBudget budget) {
        SourceFile file = file(FileCategory.PO, "PO-" + UUID.randomUUID() + ".pdf");
        var readChecks = budget.totalsChecks();
        for (int i = 0; i < readChecks.size(); i++) {
            totalsChecks.add(new TotalsCheck(file.getId(), i + 1, readChecks.get(i)));
        }
        return budgetImport.save(file, "po-protest", budget.budget(), readChecks);
    }

    /** Reads any budget (e.g. the private golden one) as the ProcessingResultService would. */
    public Budget readBudget(BudgetData read, List<TotalsCheckData> readChecks) {
        SourceFile file = file(FileCategory.PO, "PO-" + UUID.randomUUID() + ".pdf");
        for (int i = 0; i < readChecks.size(); i++) {
            totalsChecks.add(new TotalsCheck(file.getId(), i + 1, readChecks.get(i)));
        }
        return budgetImport.save(file, "po-protest", read, readChecks);
    }

    /** Pilot budget read and confirmed (fiscal year 05/2026 to 04/2027, 1.3.25 and the two funds). */
    public Budget confirmedBudget() {
        Budget budget = readBudget(PilotBudget.defaults());
        confirmation.confirm(condominiumId, budget.getId(), pilotRequest(budget), "admin");
        return budget;
    }

    /** Debit in the Condomínio fund (operating fund), in a cash flow of the trial balances category. */
    public LedgerEntry debit(String account, String name, String amount, LocalDate date) {
        var read = new LedgerEntryData(1, ledgerEntries.size() + 1, date, account, name, "", "Teste " + name,
                BigDecimal.ZERO.setScale(2), new BigDecimal(amount), BigDecimal.ZERO.setScale(2),
                new Enrichment(null, null, null, false, false));
        LedgerEntry l = new LedgerEntry(condominiumId, UUID.randomUUID(), operatingFund.getId(), read);
        ledgerEntries.add(l);
        return l;
    }

    public SourceFile file(FileCategory category, String name) {
        SourceFile a = new SourceFile(condominiumId, category, name, "c/" + name,
                UUID.randomUUID().toString().replace("-", "")
                + "0".repeat(32), 100, "application/pdf", "admin");
        files.put(a.getId(), a);
        return a;
    }

    public Optional<Budget> budgetOfMonth(java.time.YearMonth month) {
        return query.activeInMonth(condominiumId, month);
    }

    public BudgetLine line(Budget budget, String code, int index) {
        return lines.stream().filter(l -> l.getBudgetId().equals(budget.getId()) && l.getPrintedCode().equals(code))
                .sorted(Comparator.comparingInt(BudgetLine::getPosition)).toList().get(index);
    }

    /** Full request for the pilot budget: fiscal year 05/2026 to 04/2027, May minutes, 1.3.25 and the two funds. */
    public BudgetConfirmationRequest pilotRequest(Budget budget) {
        return new BudgetConfirmationRequest("2026-05", "2027-04", minutes.getId(), false, LocalDate.of(2026, 5, 20),
                List.of(new EffectiveCodeRequest(line(budget, "1.3.2", 1).getId(), "1.3.25")),
                pilotFundLinks(budget), false, false, null);
    }

    public List<FundLinkRequest> pilotFundLinks(Budget budget) {
        return List.of(new FundLinkRequest(line(budget, "1.9.1", 0).getId(), reserveFund.getId()),
                new FundLinkRequest(line(budget, "1.9.2", 0).getId(), infraWorksFund.getId()));
    }
}
