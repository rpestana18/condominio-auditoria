package br.com.condominioauditoria.api.service.budget;

import br.com.condominioauditoria.api.config.properties.BudgetProperties;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetData;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetLineData;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.TotalsCheckData;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import br.com.condominioauditoria.api.model.enums.BudgetLineType;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.budget.BudgetLineRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.service.calculator.BudgetReadingAssessment;
import br.com.condominioauditoria.api.service.calculator.BudgetReadingAssessment.BudgetCheck;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Saves the read budget (ADR 0004, step 4). Called by {@code ProcessingResultService}, in the same transaction as the
 * reading, and only for a file of the PO category. Reprocessing updates the same budget and replaces the lines; a
 * confirmed budget does not change.
 */
@Service
public class BudgetImportService {

    private final BudgetRepository budgets;
    private final BudgetLineRepository lines;
    private final BudgetProperties properties;

    public BudgetImportService(BudgetRepository budgets, BudgetLineRepository lines,
            BudgetProperties properties) {
        this.budgets = budgets;
        this.lines = lines;
        this.properties = properties;
    }

    /** Budget already confirmed (or superseded) for this file: the new reading is rejected. */
    public Optional<Budget> isLocked(SourceFile file) {
        return budgets.findByFileId(file.getId()).filter(p -> p.getStatus().isLocked());
    }

    /**
     * The file no longer produces a budget (another category or other content): the unconfirmed budget goes with it.
     */
    public void removeUnconfirmed(SourceFile file) {
        budgets.findByFileId(file.getId()).filter(p -> !p.getStatus().isLocked()).ifPresent(p -> {
            lines.deleteByBudgetId(p.getId());
            budgets.delete(p);
        });
    }

    public Budget save(SourceFile file, String parser, BudgetData read,
            List<TotalsCheckData> checks) {
        Budget budget = budgets.findByFileId(file.getId())
                .orElseGet(() -> new Budget(file.getCondominiumId(), file.getId(),
                        file.getSha256()));
        lines.deleteByBudgetId(budget.getId());
        List<BudgetLine> newLines = read.lines().stream().map(l -> line(budget, l)).toList();

        BudgetStructure structure = BudgetStructure.of(newLines);
        var assessment = BudgetReadingAssessment.assess(structure, toAssessment(checks),
                properties.roundingTolerance());
        List<String> columns = read.budgetColumns() == null ? List.of() : read.budgetColumns();
        budget.recordReading(parser, read.title(), read.printedFiscalYear(),
                columns.size() > 1 ? columns.get(0) : null, columns.isEmpty() ? null : columns.getLast(),
                assessment.status(), structure.total() == null ? null : structure.total().getBudgeted(),
                structure.printedMonthlyPlanned().orElse(null), structure.monthlyPlannedFromLines(),
                properties.roundingTolerance(), Instant.now());
        budgets.save(budget);
        lines.saveAll(newLines);
        return budget;
    }

    public static List<BudgetCheck> toAssessment(List<TotalsCheckData> checks) {
        return checks.stream().map(c -> new BudgetCheck(c.code(), c.description(), c.ok(), c.detail())).toList();
    }

    public static BudgetLine line(Budget budget, BudgetLineData l) {
        return new BudgetLine(budget, l.sequence(), l.page(), BudgetLineType.valueOf(l.type().name()), l.printedCode(),
                l.account(), l.accountText(), l.mark() == null ? null : BudgetLineMark.valueOf(l.mark().name()),
                        l.description(),
                l.previousBudgeted(), l.budgeted(), l.percentageText(), l.notes());
    }
}
