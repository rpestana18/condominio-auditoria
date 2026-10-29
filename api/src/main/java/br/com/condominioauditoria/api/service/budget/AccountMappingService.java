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
import br.com.condominioauditoria.api.dto.response.budget.StoredSheetResponse;
import br.com.condominioauditoria.api.dto.response.budget.AutomaticBySourceResponse;
import br.com.condominioauditoria.api.event.BudgetChanged;
import br.com.condominioauditoria.api.mapper.AccountMappingMapper;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.budget.AccountMapping;
import br.com.condominioauditoria.api.model.budget.AccountMappingEvent;
import br.com.condominioauditoria.api.model.budget.AccountMappingSheetItem;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.BudgetLineOrigin;
import br.com.condominioauditoria.api.model.budget.MappingTarget;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.model.enums.AccountMappingAction;
import br.com.condominioauditoria.api.model.enums.AccountMappingBatchAction;
import br.com.condominioauditoria.api.model.enums.AccountMappingFilter;
import br.com.condominioauditoria.api.model.enums.AccountMappingSource;
import br.com.condominioauditoria.api.model.enums.AccountMappingStatus;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.model.enums.BudgetLineWarningCode;
import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import br.com.condominioauditoria.api.repository.accounting.LedgerEntryRepository;
import br.com.condominioauditoria.api.repository.budget.AccountMappingEventRepository;
import br.com.condominioauditoria.api.repository.budget.AccountMappingRepository;
import br.com.condominioauditoria.api.repository.budget.AccountMappingSheetItemRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineOriginRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetSheetAccountRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.service.calculator.AccountMappingSheet;
import br.com.condominioauditoria.api.service.calculator.BudgetLineReviewCheck;
import br.com.condominioauditoria.api.service.calculator.BudgetSheetBridge;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import br.com.condominioauditoria.api.service.calculator.NameSuggestion;
import br.com.condominioauditoria.api.service.calculator.PreviousVersionCopy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
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
 * Mapping of the cash flow accounts to the budget (RF-03.1.4 and RF-03.1.5; ADR 0004, Decision 4; ADR 0009, Decision
 * 8), per confirmed budget version. Since Q54 (user, 29/10/2026) the system <b>links by itself and the link counts</b>
 * (status AUTOMATIC), in this order: previous version (what the Admin confirmed), the condominium's stored sheet, the
 * budget's support sheet (expense accounts and line origins, {@link BudgetSheetBridge}), the name ({@link
 * NameSuggestion}); then, of <b>low confidence</b>, the sibling account of the support sheet, the weak name match and
 * the supplier. Only the accounts without a link found and the low-confidence links are exceptions. The Admin changes
 * or confirms any account, with the trail; the system never touches a confirmed account, a refused link, the Admin's
 * choice or the target "sem linha da PO". Every change writes an event to the trail (insert-only).
 *
 * <p>The links are made by themselves: when the budget is confirmed, when a cash flow or a support sheet is saved
 * (through {@link #suggestPending(UUID)}, after the commit) and when the screen is opened ({@link #open}). An automatic
 * link whose rule now gives another result is updated (author "sistema"); running it again without a change writes
 * nothing.
 *
 * <p>No AI call, and the account number is never compared with the budget's (RF-00.8).
 */
@Service
public class AccountMappingService {

    private static final Logger log = LoggerFactory.getLogger(AccountMappingService.class);
    private static final Pattern ACCOUNT = Pattern.compile("^\\d{1,20}$");
    /** Author of the automatic links, in the trail (Q51, Q54). */
    public static final String SYSTEM = "sistema";

    private final CondominiumRepository condominiums;
    private final BudgetRepository budgets;
    private final BudgetLineRepository lines;
    private final AccountMappingRepository mappings;
    private final AccountMappingEventRepository events;
    private final LedgerEntryRepository ledgerEntries;
    private final AccountMappingSheetItemRepository sheetItems;
    private final BudgetLineOriginRepository origins;
    private final BudgetSheetAccountRepository sheetAccounts;
    private final NameSuggestion nameSuggestion;
    private final ApplicationEventPublisher publisher;

    public AccountMappingService(CondominiumRepository condominiums, BudgetRepository budgets,
            BudgetLineRepository lines,
            AccountMappingRepository mappings, AccountMappingEventRepository events,
                    LedgerEntryRepository ledgerEntries, AccountMappingSheetItemRepository sheetItems,
            BudgetLineOriginRepository origins, BudgetSheetAccountRepository sheetAccounts,
            AccountMappingProperties properties, ApplicationEventPublisher publisher) {
        this.condominiums = condominiums;
        this.budgets = budgets;
        this.lines = lines;
        this.mappings = mappings;
        this.events = events;
        this.ledgerEntries = ledgerEntries;
        this.sheetItems = sheetItems;
        this.origins = origins;
        this.sheetAccounts = sheetAccounts;
        this.nameSuggestion = new NameSuggestion(properties);
        this.publisher = publisher;
    }

    /**
     * Cash flow account with a debit in the Condomínio fund in the budget's fiscal year: printed name, count, sum and the
     * suppliers of the entries (only to break a tie in the suggestion by name).
     */
    record CashFlowAccount(String code, String name, int ledgerEntries, BigDecimal debits, SortedSet<String> suppliers) {

        CashFlowAccount(String code, String name, int ledgerEntries, BigDecimal debits) {
            this(code, name, ledgerEntries, debits, new TreeSet<>());
        }
    }

    /** What one generation linked or changed, by source, and the accounts left without a link found. */
    private record Generation(int fromPrevious, int fromSheet, int fromBudgetSheet, int byName, int lowConfidence,
            List<AccountWithoutSuggestionResponse> withoutSuggestion) {

        static final Generation NOTHING = new Generation(0, 0, 0, 0, 0, List.of());

        int created() {
            return fromPrevious + fromSheet + fromBudgetSheet + byName;
        }
    }

    /** An automatic link proposed by the rules for one account. */
    record Proposal(MappingTarget target, AccountMappingSource source, String reason, boolean same,
            boolean lowConfidence) {
    }

    /** The rules of one budget version, built once per generation. */
    private record Rules(List<BudgetLine> candidates, NameSuggestion.LineHints hints, BudgetSheetBridge bridge,
            List<BudgetLine> contractLines) {
    }

    /** Budget, lines and possible targets, loaded once per request. */
    /**
     * {@code targets}: every line that receives debits, by id (ADR 0007, Decision 1). {@code targetsByCode}: only the
     * codes that appear once among them, for the inputs that name a line by its code (sheet, previous version copy).
     */
    private record Context(Budget budget, List<BudgetLine> lines, BudgetStructure structure,
            Map<UUID, BudgetLine> byId, Map<UUID, BudgetLine> targets, Map<String, BudgetLine> targetsByCode) {
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
        AccountMappingSummaryResponse summary = new AccountMappingSummaryResponse(all.size(),
                count(all, AccountMappingStatus.AUTOMATIC) + count(all, AccountMappingStatus.SUGGESTED),
                count(all, AccountMappingStatus.CONFIRMED),
                (int) all.stream().filter(AccountMappingResponse::lowConfidence).count(),
                count(all, AccountMappingStatus.REJECTED),
                (int) all.stream().filter(c -> c.status() == null).count());
        AccountMappingFilter f = filter == null ? AccountMappingFilter.ALL : filter;
        return new AccountMappingsResponse(budget.getId(), budget.getVersion(), summary, automaticBySource(all),
                storedSheet(budget.getCondominiumId()), all.stream().filter(c -> matches(c, f)).toList());
    }

    /**
     * The "De-para" screen being opened (any role): first applies the automatic links (Q51, Q54), then lists. A budget
     * not confirmed yet only lists.
     */
    @Transactional
    public AccountMappingsResponse open(UUID condominiumId, UUID budgetId, AccountMappingFilter filter) {
        Budget budget = budget(condominiumId, budgetId);
        if (budget.getStatus().isLocked()) {
            generate(context(budget), SYSTEM, Instant.now(), true);
        }
        return list(condominiumId, budgetId, filter);
    }

    /**
     * Applies the automatic links of every confirmed (or superseded) budget version of the condominium (Q51, Q54).
     * Called after the budget confirmation and after a cash flow or a support sheet is saved. Publishes nothing: the
     * numbers are calculated at query time from the links (ADR 0007), and publishing here would loop.
     */
    @Transactional
    public int suggestPending(UUID condominiumId) {
        int created = 0;
        for (Budget budget : budgets.findByCondominiumIdAndStatusIn(condominiumId,
                EnumSet.of(BudgetStatus.CONFIRMED, BudgetStatus.SUPERSEDED))) {
            created += generate(context(budget), SYSTEM, Instant.now(), true).created();
        }
        return created;
    }

    /**
     * "Confirmar todas as ligações automáticas" (optional since Q54): confirms at once every account AUTOMATIC in this
     * version, one event per account in the trail. {@code expected} is the count the screen showed: when it changed in
     * the meantime (a new link came in), nothing is confirmed.
     */
    @Transactional
    public AccountMappingBatchResponse confirmAllSuggested(UUID condominiumId, UUID budgetId, Integer expected,
            String username) {
        Context ctx = writeContext(condominiumId, budgetId);
        if (expected == null || expected < 1) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Informe quantas ligações automáticas a tela mostrou");
        }
        condominiums.lockById(condominiumId);
        List<String> suggested = mappings.findByBudgetIdOrderByAccountCode(ctx.budget().getId()).stream()
                .filter(d -> d.getStatus() == AccountMappingStatus.AUTOMATIC
                        || d.getStatus() == AccountMappingStatus.SUGGESTED)
                .map(AccountMapping::getAccountCode).toList();
        if (suggested.size() != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "As ligações automáticas mudaram desde que a tela"
                    + " foi aberta: eram " + expected + ", agora são " + suggested.size() + ". Confira e confirme de"
                    + " novo.");
        }
        return changeStatus(ctx, suggested, AccountMappingStatus.CONFIRMED, username);
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
            if (l == null || !ctx.targets().containsKey(l.getId())) {
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
        // Q54: the Admin's choice always counts; confirm=false keeps it "automático" (not checked), never overwritten
        AccountMappingStatus status = request.confirm() == null || request.confirm() ? AccountMappingStatus.CONFIRMED
                : AccountMappingStatus.AUTOMATIC;
        String name = Optional.ofNullable(cashFlowAccounts(ctx.budget()).get(code)).map(CashFlowAccount::name).orElse(null);
        Instant now = Instant.now();
        AccountMapping d = save(ctx, code, name, target, status, AccountMappingSource.ADMIN, "escolhido pelo Admin",
                false, false, username, now, true);
        if (now.equals(d.getUpdatedAt())) {
            publisher.publishEvent(BudgetChanged.of(condominiumId, "de-para da conta " + code + " "
                    + (status == AccountMappingStatus.CONFIRMED ? "confirmado" : "escolhido"), username, now));
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
        AccountMappingStatus newStatus = request.action() == AccountMappingBatchAction.CONFIRM
                ? AccountMappingStatus.CONFIRMED : AccountMappingStatus.REJECTED;
        return changeStatus(ctx, request.accounts().stream().distinct().toList(), newStatus, username);
    }

    /** Confirms or rejects the accounts, one event per changed account, and publishes the change once. */
    private AccountMappingBatchResponse changeStatus(Context ctx, List<String> accounts, AccountMappingStatus newStatus,
            String username) {
        UUID condominiumId = ctx.budget().getCondominiumId();
        Map<String,
                AccountMapping> existing = byAccount(mappings.findByBudgetIdOrderByAccountCode(ctx.budget().getId()));
        Instant now = Instant.now();
        int changed = 0;
        List<SkippedAccountResponse> skipped = new ArrayList<>();
        for (String account : accounts) {
            AccountMapping d = existing.get(account);
            if (d == null) {
                skipped.add(new SkippedAccountResponse(account, "conta sem linha encontrada nesta versão: escolha o"
                        + " destino (uma linha ou \"sem linha da PO\")"));
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
        log.info("De-para da PO {}: {} conta(s) {}", ctx.budget().getId(), changed, newStatus);
        if (changed > 0) {
            String action = newStatus == AccountMappingStatus.CONFIRMED ? "confirmado" : "recusado";
            List<String> changedAccounts = accounts.stream()
                    .filter(c -> skipped.stream().noneMatch(i -> i.account().equals(c))).toList();
            publisher.publishEvent(BudgetChanged.of(condominiumId, changedAccounts.size() == 1
                    ? "de-para da conta " + changedAccounts.getFirst() + " " + action
                    : "de-para de " + changedAccounts.size() + " contas " + action, username, now));
        }
        return new AccountMappingBatchResponse(changed, skipped);
    }

    /**
     * The Admin asks for the automatic links (the screen no longer needs it, Q51, Q54): the same generation the system
     * runs by itself.
     */
    @Transactional
    public AccountMappingSuggestionsResponse suggest(UUID condominiumId, UUID budgetId, String username) {
        Context ctx = writeContext(condominiumId, budgetId);
        Generation g = generate(ctx, username, Instant.now(), false);
        return new AccountMappingSuggestionsResponse(g.created(), g.fromPrevious(), g.fromSheet(),
                g.fromBudgetSheet(), g.byName(), g.lowConfidence(), g.withoutSuggestion());
    }

    /**
     * Applies the automatic links (Q54; ADR 0009, Decision 8) to every account of this version that the system owns:
     * without a mapping, or AUTOMATIC (or the old SUGGESTED) from a rule. A confirmed account, a refused link and the
     * Admin's choice never change. Only what changed is written, one event per account, author {@code username}.
     *
     * @param quiet with nothing to change, returns at once without taking the lock (screen opening and after-commit)
     */
    private Generation generate(Context ctx, String username, Instant now, boolean quiet) {
        Budget budget = ctx.budget();
        Map<String, CashFlowAccount> cashFlowAccount = cashFlowAccounts(budget);
        Optional<Budget> previous = previousVersion(budget);
        List<AccountMapping> previousConfirmed = previous.map(p -> mappings.findByBudgetIdOrderByAccountCode(p.getId())
                .stream().filter(a -> a.getStatus() == AccountMappingStatus.CONFIRMED).toList()).orElse(List.of());
        Map<UUID, BudgetLine> previousLines = previous.map(p -> lines.findByBudgetIdOrderByPosition(p.getId()).stream()
                .collect(Collectors.toMap(BudgetLine::getId, Function.identity()))).orElse(Map.of());
        Map<String, AccountMappingSheetItem> sheet = new TreeMap<>();
        sheetItems.findByCondominiumIdOrderByAccountCode(budget.getCondominiumId())
                .forEach(i -> sheet.put(i.getAccountCode(), i));
        Map<String, AccountMapping> previousByAccount = byAccount(previousConfirmed);
        Rules rules = rules(ctx);

        SortedSet<String> codes = new TreeSet<>(cashFlowAccount.keySet());
        codes.addAll(previousByAccount.keySet());
        codes.addAll(sheet.keySet());

        Map<String, AccountMapping> existing = byAccount(mappings.findByBudgetIdOrderByAccountCode(budget.getId()));
        Map<String, Proposal> toApply = new LinkedHashMap<>();
        List<AccountWithoutSuggestionResponse> withoutSuggestion = new ArrayList<>();
        for (String code : codes) {
            AccountMapping current = existing.get(code);
            if (current != null && !ownedBySystem(current)) {
                continue;
            }
            CashFlowAccount c = cashFlowAccount.get(code);
            String name = c != null ? c.name()
                    : previousByAccount.containsKey(code) ? previousByAccount.get(code).getAccountName()
                    : current == null ? null : current.getAccountName();
            var result = propose(name, c == null ? Set.of() : c.suppliers(), previousByAccount.get(code), previousLines,
                    sheet.get(code), ctx, rules);
            if (result instanceof Proposal p) {
                if (current == null || differs(current, p, ctx)) {
                    toApply.put(code, p);
                }
            } else if (current == null) {
                withoutSuggestion.add(new AccountWithoutSuggestionResponse(code, name, (String) result));
            }
        }
        if (toApply.isEmpty() && quiet) {
            return Generation.NOTHING;
        }
        // Serializes the generators of the condominium (screen, after-commit, Admin) and reads again under the lock
        condominiums.lockById(budget.getCondominiumId());
        existing = byAccount(mappings.findByBudgetIdOrderByAccountCode(budget.getId()));
        int fromPrevious = 0;
        int fromSheet = 0;
        int fromBudgetSheet = 0;
        int byName = 0;
        int low = 0;
        for (var e : toApply.entrySet()) {
            AccountMapping current = existing.get(e.getKey());
            if (current != null && !ownedBySystem(current)) {
                continue;
            }
            Proposal p = e.getValue();
            String name = Optional.ofNullable(cashFlowAccount.get(e.getKey())).map(CashFlowAccount::name)
                    .orElse(current == null ? null : current.getAccountName());
            save(ctx, e.getKey(), name, p.target(), AccountMappingStatus.AUTOMATIC, p.source(), p.reason(), p.same(),
                    p.lowConfidence(), username, now, false);
            switch (p.source()) {
                case PREVIOUS_VERSION -> fromPrevious++;
                case SPREADSHEET -> fromSheet++;
                case BUDGET_SHEET -> fromBudgetSheet++;
                default -> byName++;
            }
            if (p.lowConfidence()) {
                low++;
            }
        }
        Generation g = new Generation(fromPrevious, fromSheet, fromBudgetSheet, byName, low,
                List.copyOf(withoutSuggestion));
        if (g.created() > 0 || !quiet) {
            log.info("De-para da PO {}: {} ligações automáticas gravadas ({} da versão anterior, {} da planilha guardada,"
                    + " {} da planilha de apoio, {} pelo nome; {} de baixa confiança), {} contas sem linha encontrada"
                    + " ({})", budget.getId(), g.created(), fromPrevious, fromSheet, fromBudgetSheet, byName, low,
                    withoutSuggestion.size(), username);
        }
        return g;
    }

    /** An account the system may link or relink: without mapping, or automatic (or old suggested) from a rule. */
    private static boolean ownedBySystem(AccountMapping d) {
        return d.getSource() != AccountMappingSource.ADMIN && (d.getStatus() == AccountMappingStatus.AUTOMATIC
                || d.getStatus() == AccountMappingStatus.SUGGESTED);
    }

    private static boolean differs(AccountMapping d, Proposal p, Context ctx) {
        return d.getStatus() != AccountMappingStatus.AUTOMATIC || !d.target(ctx.byId()).sameAs(withText(p.target(),
                ctx.byId())) || d.getSource() != p.source() || d.isLowConfidence() != p.lowConfidence()
                || d.isSameAsPreviousVersion() != p.same();
    }

    /** The rules of this version: name candidates, the Admin's marks, the support sheet bridge, the contract lines. */
    private Rules rules(Context ctx) {
        List<BudgetLine> candidates = NameSuggestion.candidates(ctx.structure());
        // O1 and O2: the Admin's marks of the line review (ADR 0009, Decision 3)
        NameSuggestion.LineHints hints = NameSuggestion.LineHints.of(candidates);
        Map<UUID, BudgetLineOrigin> byLine = new LinkedHashMap<>();
        origins.findByBudgetId(ctx.budget().getId()).forEach(o -> byLine.put(o.getBudgetLineId(), o));
        Map<UUID, List<String>> sourceAccounts = new LinkedHashMap<>();
        byLine.forEach((id, o) -> sourceAccounts.put(id, o.getSourceAccounts()));
        // O1: a line whose printed account does not match its description is reached only by the description
        java.util.Set<UUID> notByAccount = new java.util.HashSet<>(hints.descriptionPrevails());
        BudgetLineReviewCheck.check(ctx.structure(), byLine).forEach((id, warnings) -> {
            if (warnings.stream().anyMatch(w -> w.code() == BudgetLineWarningCode.ACCOUNT_DESCRIPTION_MISMATCH)) {
                notByAccount.add(id);
            }
        });
        BudgetSheetBridge bridge = new BudgetSheetBridge(nameSuggestion,
                sheetAccounts.findByBudgetIdOrderBySheetRow(ctx.budget().getId()), candidates, sourceAccounts,
                notByAccount);
        List<BudgetLine> contractLines = ctx.structure().groupsWithoutFunds().stream()
                .filter(BudgetLineReviewCheck::isContractsGroup).flatMap(g -> g.lines().stream())
                .filter(candidates::contains).toList();
        return new Rules(candidates, hints, bridge, contractLines);
    }

    /**
     * The automatic link of one account, by the order of Q54: previous version (confirmed), stored sheet, support sheet,
     * name; then, of low confidence, the sibling account of the support sheet, the weak name match and the supplier.
     * Returns a {@link Proposal} or, without a link found, the reason (String).
     */
    private Object propose(String name, java.util.Collection<String> suppliers, AccountMapping previousConfirmed,
            Map<UUID, BudgetLine> previousLines, AccountMappingSheetItem sheetItem, Context ctx, Rules rules) {
        List<String> notFound = new ArrayList<>();
        if (previousConfirmed != null) {
            var copy = PreviousVersionCopy.copy(previousConfirmed, previousLines, ctx.targetsByCode());
            if (copy instanceof PreviousVersionCopy.Copied c) {
                return new Proposal(c.target(), AccountMappingSource.PREVIOUS_VERSION, c.reason(), c.same(), false);
            }
            notFound.add(copy.reason());
        }
        if (sheetItem != null) {
            Optional<MappingTarget> target = sheetTarget(sheetItem, ctx);
            if (target.isPresent()) {
                return new Proposal(target.get(), AccountMappingSource.SPREADSHEET, "planilha guardada"
                        + (sheetItem.getFileName() == null ? "" : " " + sheetItem.getFileName()) + ", linha "
                        + sheetItem.getFileLine(), false, false);
            }
            notFound.add("planilha guardada, linha " + sheetItem.getFileLine() + ": a linha "
                    + sheetItem.getTargetLineCode() + " não existe nesta versão da PO ou se repete");
        }
        String prefix = notFound.isEmpty() ? "" : String.join("; ", notFound) + "; ";
        Optional<BudgetSheetBridge.Link> bridged = rules.bridge().link(name, suppliers, rules.hints());
        if (bridged.isPresent() && !bridged.get().lowConfidence()) {
            return new Proposal(MappingTarget.line(bridged.get().line()), AccountMappingSource.BUDGET_SHEET,
                    prefix + bridged.get().reason(), false, false);
        }
        var byName = nameSuggestion.suggest(name, suppliers, rules.candidates(), rules.hints());
        if (byName instanceof NameSuggestion.Suggested s) {
            return new Proposal(MappingTarget.line(s.line()), AccountMappingSource.NAME, prefix + s.reason(), false,
                    s.lowConfidence());
        }
        if (bridged.isPresent()) {
            return new Proposal(MappingTarget.line(bridged.get().line()), AccountMappingSource.BUDGET_SHEET,
                    prefix + bridged.get().reason(), false, true);
        }
        Optional<NameSuggestion.Suggested> weak = nameSuggestion.weakMatch(name, rules.candidates(), rules.hints())
                .or(() -> nameSuggestion.supplierMatch(suppliers, rules.contractLines()));
        if (weak.isPresent()) {
            return new Proposal(MappingTarget.line(weak.get().line()), AccountMappingSource.NAME,
                    prefix + weak.get().reason(), false, true);
        }
        return prefix + byName.reason() + (rules.bridge().isEmpty() ? "" : "; planilha de apoio da PO sem conta de"
                + " mesmo nome ligada a uma linha");
    }

    /** Target of a stored sheet line in this version: the line by its effective code (unique) or the special one. */
    private static Optional<MappingTarget> sheetTarget(AccountMappingSheetItem item, Context ctx) {
        if (item.getTargetType() != MappingTargetType.BUDGET_LINE) {
            return Optional.of(MappingTarget.special(item.getTargetType(), item.getTargetDetail()));
        }
        return Optional.ofNullable(ctx.targetsByCode().get(item.getTargetLineCode())).map(MappingTarget::line);
    }

    /** Sheet of links (CSV): everything comes in AUTOMATIC (Q54); a confirmed account does not change. */
    @Transactional
    public AccountMappingSheetResponse loadSheet(UUID condominiumId, UUID budgetId, String fileName, String content,
            String username) {
        return loadSheet(condominiumId, budgetId, fileName, content,
                sha256((content == null ? "" : content).getBytes(StandardCharsets.UTF_8)), username);
    }

    /**
     * Sheet of links (CSV): everything comes in AUTOMATIC and counts (Q54); a confirmed account and the Admin's choice do
     * not change. The accepted lines are stored as the condominium's sheet (Q51), replacing the previous one, and applied
     * again to new accounts and new budget versions.
     *
     * @param sha256 hash of the uploaded file, kept with each stored line
     */
    @Transactional
    public AccountMappingSheetResponse loadSheet(UUID condominiumId, UUID budgetId, String fileName, String content,
            String sha256, String username) {
        Context ctx = writeContext(condominiumId, budgetId);
        condominiums.lockById(condominiumId);
        List<BudgetLine> fundLines = ctx.structure().funds().map(BudgetStructure.Group::lines).orElse(List.of());
        var reading = AccountMappingSheet.read(content, List.copyOf(ctx.targets().values()), fundLines);
        Map<String, CashFlowAccount> cashFlowAccount = cashFlowAccounts(ctx.budget());
        Map<String,
                AccountMapping> existing = byAccount(mappings.findByBudgetIdOrderByAccountCode(ctx.budget().getId()));
        Instant now = Instant.now();
        String source = fileName == null || fileName.isBlank() ? "planilha" : "planilha " + fileName;
        if (!reading.items().isEmpty()) {
            sheetItems.deleteByCondominiumId(condominiumId);
            for (AccountMappingSheet.Item item : reading.items()) {
                sheetItems.save(new AccountMappingSheetItem(condominiumId, item.account(), item.target(),
                        fileName == null || fileName.isBlank() ? null : fileName, sha256, item.line(), username, now));
            }
        }
        int accepted = 0;
        List<SkippedAccountResponse> skipped = new ArrayList<>();
        for (AccountMappingSheet.Item item : reading.items()) {
            AccountMapping current = existing.get(item.account());
            if (current != null && !ownedBySystem(current)) {
                skipped.add(new SkippedAccountResponse(item.account(),
                        "já " + current.getStatus().label() + " pelo Admin (" + current.target(ctx.byId()).text()
                        + "): para mudar, altere a conta"));
                continue;
            }
            String name = Optional.ofNullable(cashFlowAccount.get(item.account())).map(CashFlowAccount::name).orElse(null);
            save(ctx, item.account(), name, item.target(), AccountMappingStatus.AUTOMATIC, AccountMappingSource.SPREADSHEET,
                    source + ", linha " + item.line(), false, false, username, now, false);
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
     * @param manual the Admin's choice: changes any account; the automatic sources only touch what the system owns
     *     ({@link #ownedBySystem})
     */
    private AccountMapping save(Context ctx, String account, String name, MappingTarget target,
            AccountMappingStatus status, AccountMappingSource source, String reason, boolean same, boolean lowConfidence,
            String username, Instant now, boolean manual) {
        MappingTarget newTarget = withText(target, ctx.byId());
        Optional<AccountMapping> existing = mappings.findByBudgetIdAndAccountCode(ctx.budget().getId(), account);
        if (existing.isEmpty()) {
            AccountMapping d = new AccountMapping(ctx.budget(), account, name, newTarget, status, source, reason, same,
                    lowConfidence, username, now);
            mappings.save(d);
            events.save(new AccountMappingEvent(d,
                    status == AccountMappingStatus.CONFIRMED ? AccountMappingAction.CONFIRMED
                    : AccountMappingAction.AUTOMATIC, null, null, newTarget, username, now));
            return d;
        }
        AccountMapping d = existing.get();
        if (!manual && !ownedBySystem(d)) {
            return d;
        }
        MappingTarget before = d.target(ctx.byId());
        AccountMappingStatus statusBefore = d.getStatus();
        boolean targetChanged = !before.sameAs(newTarget);
        if (!targetChanged && statusBefore == status && d.getSource() == source
                && d.isLowConfidence() == lowConfidence && d.isSameAsPreviousVersion() == same) {
            return d;
        }
        d.updateName(name);
        d.change(newTarget, status, source, reason, same, lowConfidence, username, now);
        mappings.save(d);
        AccountMappingAction action = targetChanged ? AccountMappingAction.CHANGED
                : status == AccountMappingStatus.CONFIRMED ? AccountMappingAction.CONFIRMED
                : status == AccountMappingStatus.REJECTED ? AccountMappingAction.REJECTED : AccountMappingAction.AUTOMATIC;
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
        Map<UUID, BudgetLine> targets = new LinkedHashMap<>();
        debitTargets(structure).forEach(l -> targets.put(l.getId(), l));
        Map<String, Long> count = targets.values().stream()
                .collect(Collectors.groupingBy(BudgetLine::getEffectiveCode, Collectors.counting()));
        Map<String, BudgetLine> byCode = new LinkedHashMap<>();
        targets.values().stream().filter(l -> count.get(l.getEffectiveCode()) == 1)
                .forEach(l -> byCode.put(l.getEffectiveCode(), l));
        return new Context(budget, readLines, structure, byId, targets, byCode);
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
            SortedSet<String> supplier = new TreeSet<>();
            if (l.getSupplier() != null && !l.getSupplier().isBlank()) {
                supplier.add(l.getSupplier().trim());
            }
            byCode.merge(l.getAccountCode(), new CashFlowAccount(l.getAccountCode(), l.getAccountName(), 1,
                    l.getDebit(), supplier),
                    (a, b) -> {
                        SortedSet<String> suppliers = new TreeSet<>(a.suppliers());
                        suppliers.addAll(b.suppliers());
                        return new CashFlowAccount(a.code(),
                                b.name() == null || b.name().isBlank() ? a.name() : b.name(),
                                a.ledgerEntries() + 1, a.debits().add(b.debits()), suppliers);
                    });
        }
        return byCode;
    }

    private static AccountMappingResponse account(CashFlowAccount c, AccountMapping d, Context ctx) {
        if (d == null) {
            return new AccountMappingResponse(c.code(), c.name(), c.ledgerEntries(), c.debits(), null, null, null,
                    null, false, false, null, null);
        }
        return new AccountMappingResponse(c.code(), c.name() != null ? c.name() : d.getAccountName(),
                c.ledgerEntries(), c.debits(),
                AccountMappingMapper.toResponse(d.target(ctx.byId())), d.getStatus(), d.getSource(), d.getReason(),
                d.isLowConfidence() && d.getStatus() == AccountMappingStatus.AUTOMATIC, d.isSameAsPreviousVersion(),
                d.getUpdatedBy(), d.getUpdatedAt());
    }

    private static boolean matches(AccountMappingResponse c, AccountMappingFilter f) {
        return switch (f) {
            case ALL -> true;
            case EXCEPTIONS -> c.status() == null || c.status() == AccountMappingStatus.REJECTED || c.lowConfidence();
            case AUTOMATIC -> c.status() == AccountMappingStatus.AUTOMATIC;
            case LOW_CONFIDENCE -> c.lowConfidence();
            case PENDING -> c.status() != AccountMappingStatus.CONFIRMED;
            case SUGGESTED -> c.status() == AccountMappingStatus.SUGGESTED;
            case CONFIRMED -> c.status() == AccountMappingStatus.CONFIRMED;
            case REJECTED -> c.status() == AccountMappingStatus.REJECTED;
            case UNMAPPED -> c.status() == null;
            case SAME_AS_PREVIOUS_VERSION -> c.sameAsPreviousVersion() && c.status() != null;
        };
    }

    /** AUTOMATIC accounts by source (Q54), shown on the screen and before "Confirmar todas". */
    private static AutomaticBySourceResponse automaticBySource(List<AccountMappingResponse> accounts) {
        Map<AccountMappingSource, Long> by = accounts.stream()
                .filter(c -> (c.status() == AccountMappingStatus.AUTOMATIC || c.status() == AccountMappingStatus.SUGGESTED)
                        && c.source() != null)
                .collect(Collectors.groupingBy(AccountMappingResponse::source, Collectors.counting()));
        return new AutomaticBySourceResponse(by.getOrDefault(AccountMappingSource.PREVIOUS_VERSION, 0L).intValue(),
                by.getOrDefault(AccountMappingSource.SPREADSHEET, 0L).intValue(),
                by.getOrDefault(AccountMappingSource.BUDGET_SHEET, 0L).intValue(),
                by.getOrDefault(AccountMappingSource.NAME, 0L).intValue(),
                by.getOrDefault(AccountMappingSource.ADMIN, 0L).intValue());
    }

    /** The condominium's stored sheet (the last one loaded), or null. */
    private StoredSheetResponse storedSheet(UUID condominiumId) {
        List<AccountMappingSheetItem> items = sheetItems.findByCondominiumIdOrderByAccountCode(condominiumId);
        if (items.isEmpty()) {
            return null;
        }
        AccountMappingSheetItem first = items.getFirst();
        return new StoredSheetResponse(first.getFileName(), first.getFileSha256(), items.size(), first.getLoadedBy(),
                first.getLoadedAt());
    }

    /** SHA-256 in hexadecimal of the uploaded sheet. */
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
