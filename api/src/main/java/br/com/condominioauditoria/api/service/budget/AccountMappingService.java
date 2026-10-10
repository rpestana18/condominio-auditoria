package br.com.condominioauditoria.api.service.budget;

import br.com.condominioauditoria.api.config.properties.AccountMappingProperties;
import br.com.condominioauditoria.api.dto.request.budget.AccountMappingBatchRequest;
import br.com.condominioauditoria.api.dto.request.budget.MappingTargetRequest;
import br.com.condominioauditoria.api.dto.response.budget.AccountMappingBatchResponse;
import br.com.condominioauditoria.api.dto.response.budget.AccountMappingEventResponse;
import br.com.condominioauditoria.api.dto.response.budget.AccountMappingResponse;
import br.com.condominioauditoria.api.dto.response.budget.AccountMappingSheetResponse;
import br.com.condominioauditoria.api.dto.response.budget.AccountMappingSuggestionsResponse;
import br.com.condominioauditoria.api.dto.response.budget.AccountMappingSummaryResponse;
import br.com.condominioauditoria.api.dto.response.budget.AccountMappingsResponse;
import br.com.condominioauditoria.api.dto.response.budget.AccountWithoutSuggestionResponse;
import br.com.condominioauditoria.api.dto.response.budget.RejectedSheetLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.SkippedAccountResponse;
import br.com.condominioauditoria.api.event.BudgetChanged;
import br.com.condominioauditoria.api.mapper.AccountMappingMapper;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.budget.AccountMapping;
import br.com.condominioauditoria.api.model.budget.AccountMappingEvent;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.MappingTarget;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.model.enums.AccountMappingAction;
import br.com.condominioauditoria.api.model.enums.AccountMappingBatchAction;
import br.com.condominioauditoria.api.model.enums.AccountMappingFilter;
import br.com.condominioauditoria.api.model.enums.AccountMappingSource;
import br.com.condominioauditoria.api.model.enums.AccountMappingStatus;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import br.com.condominioauditoria.api.repository.accounting.LedgerEntryRepository;
import br.com.condominioauditoria.api.repository.budget.AccountMappingEventRepository;
import br.com.condominioauditoria.api.repository.budget.AccountMappingRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.service.calculator.AccountMappingSheet;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import br.com.condominioauditoria.api.service.calculator.NameSuggestion;
import br.com.condominioauditoria.api.service.calculator.PreviousVersionCopy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Mapping of the cash flow accounts to the budget (RF-03.1.4 and RF-03.1.5; ADR 0004, Decision 4), per confirmed budget
 * version. Suggestions (previous version, sheet, name) always come in as SUGGESTED and change no number: budget vs.
 * actual only uses what the Admin confirmed. Every change writes an event to the trail (insert-only).
 *
 * <p>No AI call: the suggestion by name is the pure function {@link NameSuggestion}.
 */
@Service
public class AccountMappingService {

    private static final Logger log = LoggerFactory.getLogger(AccountMappingService.class);
    private static final Pattern ACCOUNT = Pattern.compile("^\\d{1,20}$");

    private final CondominiumRepository condominiums;
    private final BudgetRepository budgets;
    private final BudgetLineRepository lines;
    private final AccountMappingRepository mappings;
    private final AccountMappingEventRepository events;
    private final LedgerEntryRepository ledgerEntries;
    private final NameSuggestion nameSuggestion;
    private final ApplicationEventPublisher publisher;

    public AccountMappingService(CondominiumRepository condominiums, BudgetRepository budgets,
            BudgetLineRepository lines,
            AccountMappingRepository mappings, AccountMappingEventRepository events,
                    LedgerEntryRepository ledgerEntries,
            AccountMappingProperties properties, ApplicationEventPublisher publisher) {
        this.condominiums = condominiums;
        this.budgets = budgets;
        this.lines = lines;
        this.mappings = mappings;
        this.events = events;
        this.ledgerEntries = ledgerEntries;
        this.nameSuggestion = new NameSuggestion(properties);
        this.publisher = publisher;
    }

    /**
     * Cash flow account with a debit in the Condomínio fund in the budget's fiscal year: printed name, count and sum.
     */
    record CashFlowAccount(String code, String name, int ledgerEntries, BigDecimal debits) {
    }

    /** Budget, lines and possible targets, loaded once per request. */
    private record Context(Budget budget, List<BudgetLine> lines, BudgetStructure structure,
            Map<UUID, BudgetLine> byId, Map<String, BudgetLine> targetsByCode) {
    }

    @Transactional(readOnly = true)
    public AccountMappingsResponse list(UUID condominiumId, UUID budgetId, AccountMappingFilter filter) {
        Budget budget = budget(condominiumId, budgetId);
        Context ctx = context(budget);
        Map<String,
                CashFlowAccount> cashFlowAccount = budget.getStatus().isLocked() ? cashFlowAccounts(budget) : Map.of();
        Map<String, AccountMapping> existing = byAccount(mappings.findByBudgetIdOrderByAccountCode(budget.getId()));

        TreeMap<String, AccountMappingResponse> accounts = new TreeMap<>();
        cashFlowAccount.forEach((code, c) -> accounts.put(code, account(c, existing.get(code), ctx)));
        existing.forEach((code, d) -> accounts.computeIfAbsent(code, k -> account(
                new CashFlowAccount(code, d.getAccountName(), 0, BigDecimal.ZERO.setScale(2)), d, ctx)));

        List<AccountMappingResponse> all = List.copyOf(accounts.values());
        AccountMappingSummaryResponse summary = new AccountMappingSummaryResponse(all.size(), count(all,
                AccountMappingStatus.CONFIRMED),
                count(all, AccountMappingStatus.SUGGESTED), count(all, AccountMappingStatus.REJECTED),
                (int) all.stream().filter(c -> c.status() == null).count());
        AccountMappingFilter f = filter == null ? AccountMappingFilter.ALL : filter;
        return new AccountMappingsResponse(budget.getId(), budget.getVersion(), summary,
                all.stream().filter(c -> matches(c, f)).toList());
    }

    /** The Admin chooses an account's target (from the budget's line list or a special target). */
    @Transactional
    public AccountMappingResponse setTarget(UUID condominiumId, UUID budgetId, String account,
            MappingTargetRequest request, String username) {
        Context ctx = writeContext(condominiumId, budgetId);
        String code = validateAccount(account);
        if (request == null || request.type() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Informe o tipo de destino");
        }
        MappingTarget target;
        if (request.type() == MappingTargetType.BUDGET_LINE) {
            BudgetLine l = request.lineId() == null ? null : ctx.byId().get(request.lineId());
            if (l == null || !ctx.targetsByCode().containsValue(l)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, l == null
                        ? "A linha informada não é desta PO"
                        : "A linha " + l.getEffectiveCode() + " não recebe débitos: as linhas 1.9 são comparadas com"
                                + " a arrecadação do fundo e as linhas de total e grupo não têm lançamento");
            }
            target = MappingTarget.line(l);
        } else {
            if (request.lineId() != null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "Destino " + request.type() + " não tem linha da PO");
            }
            target = MappingTarget.special(request.type(), request.detail());
        }
        AccountMappingStatus status = request.confirm() == null || request.confirm() ? AccountMappingStatus.CONFIRMED
                : AccountMappingStatus.SUGGESTED;
        String name = Optional.ofNullable(cashFlowAccounts(ctx.budget()).get(code)).map(CashFlowAccount::name).orElse(null);
        Instant now = Instant.now();
        AccountMapping d = save(ctx, code, name, target, status, AccountMappingSource.ADMIN, "escolhido pelo Admin",
                false,
                username, now, true);
        if (now.equals(d.getUpdatedAt())) {
            publisher.publishEvent(BudgetChanged.of(condominiumId, "de-para da conta " + code + " "
                    + (status == AccountMappingStatus.CONFIRMED ? "confirmado" : "sugerido"), username, now));
        }
        return account(Optional.ofNullable(cashFlowAccounts(ctx.budget()).get(code))
                .orElse(new CashFlowAccount(code, name, 0, BigDecimal.ZERO.setScale(2))), d, ctx);
    }

    /** Confirm or reject in batch (RF-03.1.13), one event per changed account. */
    @Transactional
    public AccountMappingBatchResponse batch(UUID condominiumId, UUID budgetId, AccountMappingBatchRequest request,
            String username) {
        Context ctx = writeContext(condominiumId, budgetId);
        if (request == null || request.action() == null || request.accounts() == null || request.accounts().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Informe a ação e as contas");
        }
        Map<String,
                AccountMapping> existing = byAccount(mappings.findByBudgetIdOrderByAccountCode(ctx.budget().getId()));
        AccountMappingStatus newStatus = request.action() == AccountMappingBatchAction.CONFIRM ? AccountMappingStatus.CONFIRMED : AccountMappingStatus.REJECTED;
        Instant now = Instant.now();
        int changed = 0;
        List<SkippedAccountResponse> skipped = new ArrayList<>();
        for (String account : request.accounts().stream().distinct().toList()) {
            AccountMapping d = existing.get(account);
            if (d == null) {
                skipped.add(new SkippedAccountResponse(account, "conta sem de-para nesta versão: escolha o destino"));
            } else if (d.getStatus() == newStatus) {
                skipped.add(new SkippedAccountResponse(account, "já está " + newStatus.label()));
            } else {
                MappingTarget target = d.target(ctx.byId());
                AccountMappingStatus before = d.getStatus();
                d.changeStatus(newStatus, username, now);
                mappings.save(d);
                events.save(new AccountMappingEvent(d,
                        newStatus == AccountMappingStatus.CONFIRMED ? AccountMappingAction.CONFIRMED
                        : AccountMappingAction.REJECTED, target, before, target, username, now));
                changed++;
            }
        }
        log.info("De-para da PO {}: {} conta(s) {} por {}", ctx.budget().getId(), changed, newStatus, username);
        if (changed > 0) {
            String action = newStatus == AccountMappingStatus.CONFIRMED ? "confirmado" : "recusado";
            List<String> changedAccounts = request.accounts().stream().distinct()
                    .filter(c -> skipped.stream().noneMatch(i -> i.account().equals(c))).toList();
            publisher.publishEvent(BudgetChanged.of(condominiumId, changedAccounts.size() == 1
                    ? "de-para da conta " + changedAccounts.getFirst() + " " + action
                    : "de-para de " + changedAccounts.size() + " contas " + action, username, now));
        }
        return new AccountMappingBatchResponse(changed, skipped);
    }

    /**
     * Generates suggestions for the cash flow accounts that have no mapping in this version yet: first the copy of the
     * previous version (only what was confirmed), then the name. Nothing is confirmed.
     */
    @Transactional
    public AccountMappingSuggestionsResponse suggest(UUID condominiumId, UUID budgetId, String username) {
        Context ctx = writeContext(condominiumId, budgetId);
        Map<String, CashFlowAccount> cashFlowAccount = cashFlowAccounts(ctx.budget());
        Map<String,
                AccountMapping> existing = byAccount(mappings.findByBudgetIdOrderByAccountCode(ctx.budget().getId()));
        Instant now = Instant.now();
        List<BudgetLine> candidates = NameSuggestion.candidates(ctx.structure());

        Map<String, String> previousReason = new LinkedHashMap<>();
        int fromPrevious = 0;
        int byName = 0;
        Optional<Budget> previous = previousVersion(ctx.budget());
        if (previous.isPresent()) {
            Map<UUID, BudgetLine> previousLines = lines.findByBudgetIdOrderByPosition(previous.get().getId()).stream()
                    .collect(Collectors.toMap(BudgetLine::getId, Function.identity()));
            for (AccountMapping a : mappings.findByBudgetIdOrderByAccountCode(previous.get().getId())) {
                if (a.getStatus() != AccountMappingStatus.CONFIRMED || existing.containsKey(a.getAccountCode())) {
                    continue;
                }
                var copy = PreviousVersionCopy.copy(a, previousLines, ctx.targetsByCode());
                if (copy instanceof PreviousVersionCopy.Copied c) {
                    String name = Optional.ofNullable(cashFlowAccount.get(a.getAccountCode())).map(CashFlowAccount::name)
                            .orElse(a.getAccountName());
                    existing.put(a.getAccountCode(), save(ctx, a.getAccountCode(), name, c.target(),
                            AccountMappingStatus.SUGGESTED, AccountMappingSource.PREVIOUS_VERSION, c.reason(), c.same(),
                                    username, now,
                            false));
                    fromPrevious++;
                } else {
                    previousReason.put(a.getAccountCode(), copy.reason());
                }
            }
        }

        List<AccountWithoutSuggestionResponse> withoutSuggestion = new ArrayList<>();
        Map<String, String> pending = new TreeMap<>();
        cashFlowAccount.forEach((code, c) -> pending.put(code, c.name()));
        previousReason.keySet().forEach(code -> pending.putIfAbsent(code, null));
        for (var e : pending.entrySet()) {
            String code = e.getKey();
            if (existing.containsKey(code)) {
                continue;
            }
            String prefix = previousReason.containsKey(code) ? previousReason.get(code) + "; " : "";
            var r = nameSuggestion.suggest(e.getValue(), candidates);
            if (r instanceof NameSuggestion.Suggested s) {
                existing.put(code, save(ctx, code, e.getValue(), MappingTarget.line(s.line()),
                        AccountMappingStatus.SUGGESTED,
                        AccountMappingSource.NAME, prefix + s.reason(), false, username, now, false));
                byName++;
            } else {
                withoutSuggestion.add(new AccountWithoutSuggestionResponse(code, e.getValue(), prefix + r.reason()));
            }
        }
        log.info("De-para da PO {}: {} sugestões da versão anterior, {} pelo nome, {} sem sugestão",
                ctx.budget().getId(),
                fromPrevious, byName, withoutSuggestion.size());
        return new AccountMappingSuggestionsResponse(fromPrevious + byName, fromPrevious, byName, withoutSuggestion);
    }

    /** Suggestion sheet (CSV): everything comes in as SUGGESTED; an account already confirmed does not change. */
    @Transactional
    public AccountMappingSheetResponse loadSheet(UUID condominiumId, UUID budgetId, String fileName, String content,
            String username) {
        Context ctx = writeContext(condominiumId, budgetId);
        List<BudgetLine> fundLines = ctx.structure().funds().map(BudgetStructure.Group::lines).orElse(List.of());
        var reading = AccountMappingSheet.read(content, List.copyOf(ctx.targetsByCode().values()), fundLines);
        Map<String, CashFlowAccount> cashFlowAccount = cashFlowAccounts(ctx.budget());
        Map<String,
                AccountMapping> existing = byAccount(mappings.findByBudgetIdOrderByAccountCode(ctx.budget().getId()));
        Instant now = Instant.now();
        String source = fileName == null || fileName.isBlank() ? "planilha" : "planilha " + fileName;
        int accepted = 0;
        List<SkippedAccountResponse> skipped = new ArrayList<>();
        for (AccountMappingSheet.Item item : reading.items()) {
            AccountMapping current = existing.get(item.account());
            if (current != null && current.getStatus() == AccountMappingStatus.CONFIRMED) {
                skipped.add(new SkippedAccountResponse(item.account(),
                        "já confirmada (" + current.target(ctx.byId()).text()
                        + "): para mudar, altere a conta"));
                continue;
            }
            String name = Optional.ofNullable(cashFlowAccount.get(item.account())).map(CashFlowAccount::name).orElse(null);
            save(ctx, item.account(), name, item.target(), AccountMappingStatus.SUGGESTED, AccountMappingSource.SPREADSHEET,
                    source + ", linha " + item.line(), false, username, now, false);
            accepted++;
        }
        log.info("Planilha do de-para da PO {}: {} aceitas, {} ignoradas, {} recusadas", ctx.budget().getId(), accepted,
                skipped.size(), reading.rejections().size());
        return new AccountMappingSheetResponse(accepted, skipped, reading.rejections().stream()
                .map(r -> new RejectedSheetLineResponse(r.line(), r.content(), r.reason())).toList());
    }

    @Transactional(readOnly = true)
    public List<AccountMappingEventResponse> events(UUID condominiumId, UUID budgetId) {
        return events.findByBudgetIdOrderByOccurredAtAscAccountCodeAsc(budget(condominiumId, budgetId).getId()).stream()
                .map(AccountMappingMapper::toResponse).toList();
    }

    /**
     * Saves (creates or changes) an account's mapping and the event. Without a change of target or status, saves
     * nothing.
     *
     * @param manual the Admin's choice: changes even a confirmed account; the automatic sources never touch a
     *     confirmed one
     */
    private AccountMapping save(Context ctx, String account, String name, MappingTarget target,
            AccountMappingStatus status,
            AccountMappingSource source, String reason, boolean same, String username, Instant now, boolean manual) {
        MappingTarget newTarget = withText(target, ctx.byId());
        Optional<AccountMapping> existing = mappings.findByBudgetIdAndAccountCode(ctx.budget().getId(), account);
        if (existing.isEmpty()) {
            AccountMapping d = new AccountMapping(ctx.budget(), account, name, newTarget, status, source, reason, same,
                    username, now);
            mappings.save(d);
            events.save(new AccountMappingEvent(d,
                    status == AccountMappingStatus.CONFIRMED ? AccountMappingAction.CONFIRMED
                    : AccountMappingAction.SUGGESTED, null, null, newTarget, username, now));
            return d;
        }
        AccountMapping d = existing.get();
        if (!manual && d.getStatus() == AccountMappingStatus.CONFIRMED) {
            return d;
        }
        MappingTarget before = d.target(ctx.byId());
        AccountMappingStatus statusBefore = d.getStatus();
        boolean targetChanged = !before.sameAs(newTarget);
        if (!targetChanged && statusBefore == status) {
            return d;
        }
        d.updateName(name);
        d.change(newTarget, status, source, reason, same, username, now);
        mappings.save(d);
        AccountMappingAction action = targetChanged ? AccountMappingAction.CHANGED
                : status == AccountMappingStatus.CONFIRMED ? AccountMappingAction.CONFIRMED
                : status == AccountMappingStatus.REJECTED ? AccountMappingAction.REJECTED : AccountMappingAction.SUGGESTED;
        events.save(new AccountMappingEvent(d, action, before, statusBefore, newTarget, username, now));
        return d;
    }

    private static MappingTarget withText(MappingTarget d, Map<UUID, BudgetLine> byId) {
        BudgetLine l = d.budgetLineId() == null ? null : byId.get(d.budgetLineId());
        return l == null ? d : MappingTarget.line(l);
    }

    private Budget budget(UUID condominiumId, UUID budgetId) {
        return budgets.findByIdAndCondominiumId(budgetId, condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO não encontrada"));
    }

    private Context writeContext(UUID condominiumId, UUID budgetId) {
        Budget budget = budget(condominiumId, budgetId);
        if (!budget.getStatus().isLocked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Confirme a PO antes do de-para: o de-para é por versão confirmada da PO");
        }
        return context(budget);
    }

    private Context context(Budget budget) {
        List<BudgetLine> readLines = lines.findByBudgetIdOrderByPosition(budget.getId());
        BudgetStructure structure = BudgetStructure.of(readLines);
        Map<UUID, BudgetLine> byId = readLines.stream().collect(Collectors.toMap(BudgetLine::getId,
                Function.identity()));
        Map<String, BudgetLine> targets = new LinkedHashMap<>();
        debitTargets(structure).forEach(l -> targets.putIfAbsent(l.getEffectiveCode(), l));
        return new Context(budget, readLines, structure, byId, targets);
    }

    /** Lines that can receive a debit: lines of the expense groups (1.1 to 1.8), never total, group or fund. */
    public static List<BudgetLine> debitTargets(BudgetStructure structure) {
        return structure.groupsWithoutFunds().stream().flatMap(g -> g.lines().stream()).toList();
    }

    /** Confirmed version right before this budget in the condominium (highest version lower than this one). */
    private Optional<Budget> previousVersion(Budget budget) {
        if (budget.getVersion() == null) {
            return Optional.empty();
        }
        return budgets.findByCondominiumIdAndStatusIn(budget.getCondominiumId(),
                        EnumSet.of(BudgetStatus.CONFIRMED, BudgetStatus.SUPERSEDED)).stream()
                .filter(p -> p.getVersion() != null && p.getVersion() < budget.getVersion())
                .max(Comparator.comparing(Budget::getVersion));
    }

    /**
     * Accounts with a debit in the Condomínio fund (confirmed operating fund) in the budget's fiscal year and in the
     * extended months, by code.
     */
    Map<String, CashFlowAccount> cashFlowAccounts(Budget budget) {
        if (budget.getFiscalYearStart() == null) {
            return Map.of();
        }
        UUID operating = condominiums.findById(budget.getCondominiumId()).map(Condominium::getOperatingFundId).orElse(null);
        if (operating == null) {
            return Map.of();
        }
        LocalDate start = budget.getFiscalYearStart().atDay(1);
        // The extended months use this budget's mapping (RF-11.3)
        LocalDate end = Optional.ofNullable(budget.getExtendedUntil()).orElse(budget.getFiscalYearEnd()).atEndOfMonth();
        return group(ledgerEntries.debitsWithAccount(budget.getCondominiumId(), operating, start, end));
    }

    /** Groups by code; the name is the one of the latest entry (the list comes ordered by date). */
    public static Map<String, CashFlowAccount> group(List<LedgerEntry> debits) {
        Map<String, CashFlowAccount> byCode = new TreeMap<>();
        for (LedgerEntry l : debits) {
            byCode.merge(l.getAccountCode(), new CashFlowAccount(l.getAccountCode(), l.getAccountName(), 1,
                    l.getDebit()),
                    (a, b) -> new CashFlowAccount(a.code(),
                            b.name() == null || b.name().isBlank() ? a.name() : b.name(),
                            a.ledgerEntries() + 1, a.debits().add(b.debits())));
        }
        return byCode;
    }

    private static AccountMappingResponse account(CashFlowAccount c, AccountMapping d, Context ctx) {
        if (d == null) {
            return new AccountMappingResponse(c.code(), c.name(), c.ledgerEntries(), c.debits(), null, null, null,
                    null, false,
                    null, null);
        }
        return new AccountMappingResponse(c.code(), c.name() != null ? c.name() : d.getAccountName(),
                c.ledgerEntries(), c.debits(),
                AccountMappingMapper.toResponse(d.target(ctx.byId())), d.getStatus(), d.getSource(), d.getReason(),
                d.isSameAsPreviousVersion(), d.getUpdatedBy(), d.getUpdatedAt());
    }

    private static boolean matches(AccountMappingResponse c, AccountMappingFilter f) {
        return switch (f) {
            case ALL -> true;
            case PENDING -> c.status() != AccountMappingStatus.CONFIRMED;
            case SUGGESTED -> c.status() == AccountMappingStatus.SUGGESTED;
            case CONFIRMED -> c.status() == AccountMappingStatus.CONFIRMED;
            case REJECTED -> c.status() == AccountMappingStatus.REJECTED;
            case UNMAPPED -> c.status() == null;
            case SAME_AS_PREVIOUS_VERSION -> c.sameAsPreviousVersion() && c.status() != null;
        };
    }

    private static int count(List<AccountMappingResponse> accounts, AccountMappingStatus status) {
        return (int) accounts.stream().filter(c -> c.status() == status).count();
    }

    private static Map<String, AccountMapping> byAccount(List<AccountMapping> list) {
        Map<String, AccountMapping> m = new LinkedHashMap<>();
        list.forEach(d -> m.put(d.getAccountCode(), d));
        return m;
    }

    private static String validateAccount(String account) {
        String c = account == null ? "" : account.trim();
        if (!ACCOUNT.matcher(c).matches()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Conta do fluxo \"" + account + "\" não é um código numérico");
        }
        return c;
    }
}
