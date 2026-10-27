package br.com.condominioauditoria.api.service.budget;

import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetLineOriginData;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetLineOriginsData;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.BudgetLineOrigin;
import br.com.condominioauditoria.api.model.enums.OriginMethod;
import br.com.condominioauditoria.api.model.enums.OriginSource;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.budget.BudgetLineOriginRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.service.calculator.BudgetLineReviewCheck;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Saves the origin of the lines read from the budget's support sheet (RF-13.13; ADR 0009, Decision 3 B). The sheet
 * lines are matched to the budget's expense lines by their order (the sheet has the same lines as the PO, in the same
 * order), checked by the account codes: the budget chosen is the most recent one of the condominium with the same number
 * of expense lines and at least {@link #MIN_ACCOUNT_MATCH_PERCENT}% of the lines with the same accounts. An origin the
 * Admin filled in is never replaced by the sheet. Without a matching budget nothing is saved and the file message says
 * so (conservative: never ties a sheet to the wrong budget).
 */
@Service
public class BudgetLineOriginService {

    private static final Logger log = LoggerFactory.getLogger(BudgetLineOriginService.class);

    /** Minimum share of lines whose account codes match, so that the sheet is tied to a budget. */
    public static final int MIN_ACCOUNT_MATCH_PERCENT = 80;

    private final BudgetRepository budgets;
    private final BudgetLineRepository lines;
    private final BudgetLineOriginRepository origins;

    public BudgetLineOriginService(BudgetRepository budgets, BudgetLineRepository lines,
            BudgetLineOriginRepository origins) {
        this.budgets = budgets;
        this.lines = lines;
        this.origins = origins;
    }

    /** Result of a support sheet: the budget it was tied to (null when none) and the message for the file. */
    public record Saved(Budget budget, int saved, int keptFromAdmin, String message) {
    }

    /** Called inside the transaction that saves the processing result. */
    public Saved saveFromSheet(SourceFile file, BudgetLineOriginsData data) {
        List<BudgetLineOriginData> sheet = data.lines() == null ? List.of() : data.lines().stream()
                .sorted(java.util.Comparator.comparingInt(BudgetLineOriginData::sequence)).toList();
        if (sheet.isEmpty()) {
            return new Saved(null, 0, 0, "Planilha de apoio lida, mas sem linhas de despesa reconhecidas");
        }
        Budget chosen = null;
        List<BudgetLine> expense = null;
        for (Budget b : budgets.findByCondominiumIdOrderByReadAtDesc(file.getCondominiumId())) {
            List<BudgetLine> e = expenseLines(lines.findByBudgetIdOrderByPosition(b.getId()));
            if (e.size() == sheet.size() && matchPercent(e, sheet) >= MIN_ACCOUNT_MATCH_PERCENT) {
                chosen = b;
                expense = e;
                break;
            }
        }
        if (chosen == null) {
            return new Saved(null, 0, 0, "Planilha de apoio lida (" + sheet.size() + " linhas de despesa), mas"
                    + " nenhuma PO do condomínio tem as mesmas linhas: envie e leia a PO e depois reprocesse a planilha");
        }
        Map<UUID, BudgetLineOrigin> existing = new HashMap<>();
        origins.findByBudgetId(chosen.getId()).forEach(o -> existing.put(o.getBudgetLineId(), o));
        Instant now = Instant.now();
        int saved = 0;
        int kept = 0;
        List<BudgetLineOrigin> toSave = new ArrayList<>();
        for (int i = 0; i < sheet.size(); i++) {
            BudgetLine line = expense.get(i);
            BudgetLineOriginData d = sheet.get(i);
            BudgetLineOrigin o = existing.get(line.getId());
            if (o != null && o.getSource() == OriginSource.ADMIN) {
                kept++;
                continue;
            }
            if (o == null) {
                o = new BudgetLineOrigin(line);
            }
            o.set(d.sourceAccounts(), month(d.averageFrom()), month(d.averageTo()), d.averageAmount(),
                    d.baseAmount(), d.method() == null ? null : OriginMethod.valueOf(d.method()), d.indexName(),
                    d.indexRate(), d.indexForecast(), d.indexNote(), d.finalAmount(), d.reason());
            o.source(OriginSource.SHEET, file.getId(), file.getOriginalName(), data.sheet(), d.row(),
                    file.getSha256(), "sistema", now);
            toSave.add(o);
            saved++;
        }
        origins.saveAll(toSave);
        log.info("Origem de {} linha(s) da PO {} gravada da planilha {}", saved, chosen.getId(),
                file.getOriginalName());
        String version = chosen.getVersion() == null ? "lida" : "versão " + chosen.getVersion();
        return new Saved(chosen, saved, kept, "Planilha de apoio lida: origem de " + saved + " linha(s) da PO ("
                + version + ")" + (kept == 0 ? "" : "; " + kept + " origem(ns) informada(s) pelo Admin mantida(s)")
                + ". Confira na tela \"PO aprovada\"");
    }

    /** Expense lines (groups 1.1 to 1.8) of a budget, in budget order. */
    static List<BudgetLine> expenseLines(List<BudgetLine> budgetLines) {
        List<BudgetLine> e = new ArrayList<>();
        BudgetStructure.of(budgetLines).groupsWithoutFunds().forEach(g -> e.addAll(g.lines()));
        return e;
    }

    static int matchPercent(List<BudgetLine> expense, List<BudgetLineOriginData> sheet) {
        int same = 0;
        for (int i = 0; i < expense.size(); i++) {
            List<String> a = BudgetLineReviewCheck.accountCodes(expense.get(i).getAccount());
            List<String> b = sheet.get(i).sourceAccounts() == null ? List.of() : sheet.get(i).sourceAccounts();
            if (a.equals(b)) {
                same++;
            }
        }
        return expense.isEmpty() ? 0 : same * 100 / expense.size();
    }

    private static YearMonth month(String text) {
        return text == null || text.isBlank() ? null : YearMonth.parse(text);
    }
}
