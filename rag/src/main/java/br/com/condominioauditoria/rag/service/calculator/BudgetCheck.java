package br.com.condominioauditoria.rag.service.calculator;

import static br.com.condominioauditoria.rag.util.BrazilianMoney.format;

import br.com.condominioauditoria.rag.model.TotalsCheck;
import br.com.condominioauditoria.rag.model.budget.Budget;
import br.com.condominioauditoria.rag.model.budget.BudgetLine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks the budget read against itself (RF-03.1.2, ADR 0004 Decision 1). If some number was read wrong, some sum does
 * not match. Same format as the cash flow checks: code, ok and a readable detail with both sums.
 *
 * <ul>
 *   <li>{@code SUBTOTAL_GRUPO}, one per group: sum of the lines = printed subtotal.</li>
 *   <li>{@code TOTAL}: sum of the printed subtotals = printed total.</li>
 *   <li>{@code PREVISTO_MES}: total − funds = sum of the subtotals of the other groups.</li>
 *   <li>{@code FUNDO_TAXA}: each fund = rate of the "%" column over the monthly planned (RF-03.1.3).</li>
 *   <li>{@code CODIGO_REPETIDO}: line code printed more than once.</li>
 * </ul>
 *
 * <p>The comparison is exact, to the cent, with no tolerance: a printed subtotal rounded at the source shows up as a
 * difference, with both sums, and the Admin decides (RF-03.1.2, Q29).
 *
 * <p>The groups are the lines read after each GRUPO line, in document order (not by the code prefix, which may be
 * repeated or wrong).
 */
public final class BudgetCheck {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final Pattern PERCENTAGE = Pattern.compile("^-?\\d+(\\.\\d{3})*,\\d+%$");

    private BudgetCheck() {
    }

    public static List<TotalsCheck> check(Budget budget) {
        Structure e = Structure.of(budget);
        List<TotalsCheck> result = new ArrayList<>();
        for (Group g : e.groups()) {
            result.add(subtotalCheck(g));
        }
        if (!e.withoutGroup().isEmpty()) {
            result.add(new TotalsCheck("LINHA_SEM_GRUPO", "Toda linha pertence a um grupo", false,
                    "linhas antes do primeiro grupo: " + e.withoutGroup().stream().map(BudgetLine::printedCode)
                            .collect(Collectors.joining(", "))));
        }
        result.add(totalCheck(e));
        result.add(monthlyPlannedCheck(e));
        if (e.total() != null) {
            e.funds().ifPresent(f -> result.add(fundRateCheck(f, e)));
        }
        result.add(repeatedCodeCheck(budget));
        return result;
    }

    /** Monthly planned as printed: total − funds. Empty without a total line or without a funds group. */
    public static Optional<BigDecimal> monthlyPlanned(Budget budget) {
        Structure e = Structure.of(budget);
        if (e.total() == null || e.funds().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(e.total().budgeted().subtract(e.funds().get().line().budgeted()));
    }

    private static TotalsCheck subtotalCheck(Group g) {
        BudgetLine group = g.line();
        BigDecimal sum = g.lineSum();
        BigDecimal printed = group.budgeted();
        String description = "Soma das linhas do grupo " + group.printedCode() + " = subtotal impresso";
        String name = group.printedCode() + " " + group.description();
        if (sum.compareTo(printed) == 0) {
            return new TotalsCheck("SUBTOTAL_GRUPO", description, true,
                    "%s: soma das linhas %s; impresso %s".formatted(name, format(sum), format(printed)));
        }
        return new TotalsCheck("SUBTOTAL_GRUPO", description, false,
                "%s: soma das linhas %s; impresso %s; diferença %s".formatted(name, format(sum),
                        format(printed), format(printed.subtract(sum))));
    }

    private static TotalsCheck totalCheck(Structure e) {
        String description = "Soma dos grupos = total impresso";
        if (e.total() == null) {
            return new TotalsCheck("TOTAL", description, false, "linha de total (código 1) não encontrada");
        }
        BigDecimal sum = e.groups().stream().map(g -> g.line().budgeted()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal printed = e.total().budgeted();
        boolean ok = sum.compareTo(printed) == 0;
        return new TotalsCheck("TOTAL", description, ok, "soma dos grupos %s; impresso %s%s".formatted(format(sum),
                format(printed), ok ? "" : "; diferença " + format(printed.subtract(sum))));
    }

    /**
     * Total − funds, checked against the sum of the printed subtotals of the other groups. When the sum of those
     * groups' lines is different (rounding at the source), the detail shows both; the difference itself already shows
     * up in the group's SUBTOTAL_GRUPO.
     */
    private static TotalsCheck monthlyPlannedCheck(Structure e) {
        String description = "Previsto do mês = total menos os fundos";
        if (e.total() == null || e.funds().isEmpty()) {
            return new TotalsCheck("PREVISTO_MES", description, false,
                    e.total() == null ? "linha de total não encontrada" : "grupo de fundos não encontrado");
        }
        BigDecimal total = e.total().budgeted();
        BigDecimal funds = e.funds().get().line().budgeted();
        BigDecimal planned = total.subtract(funds);
        BigDecimal subtotals = e.subtotalSumWithoutFunds();
        BigDecimal lines = e.lineSumWithoutFunds();
        boolean ok = planned.compareTo(subtotals) == 0;
        String detail = "%s - %s = %s".formatted(format(total), format(funds), format(planned));
        if (!ok) {
            detail += "; soma dos subtotais dos demais grupos " + format(subtotals);
        }
        if (lines.compareTo(planned) != 0) {
            detail += "; soma das linhas dos demais grupos " + format(lines);
        }
        return new TotalsCheck("PREVISTO_MES", description, ok, detail);
    }

    /** RF-03.1.3: each fund is the rate printed in the "%" column over the monthly planned (total − funds). */
    private static TotalsCheck fundRateCheck(Group funds, Structure e) {
        BigDecimal planned = e.total().budgeted().subtract(funds.line().budgeted());
        List<String> parts = new ArrayList<>();
        boolean ok = !funds.lines().isEmpty();
        for (BudgetLine l : funds.lines()) {
            Optional<BigDecimal> rate = rate(l.percentageText());
            if (rate.isEmpty()) {
                ok = false;
                parts.add("%s %s: sem taxa na coluna %%".formatted(l.printedCode(), l.description()));
                continue;
            }
            BigDecimal expected = apply(rate.get(), planned);
            boolean matches = expected.compareTo(l.budgeted()) == 0;
            ok &= matches;
            parts.add("%s %s: %s%% de %s = %s; impresso %s".formatted(l.printedCode(), l.description(),
                    format(rate.get()), format(planned), format(expected), format(l.budgeted())));
        }
        return new TotalsCheck("FUNDO_TAXA", "Cada fundo = taxa da coluna % sobre o previsto do mês", ok,
                parts.isEmpty() ? "grupo de fundos sem linhas" : String.join("; ", parts));
    }

    private static TotalsCheck repeatedCodeCheck(Budget budget) {
        Map<String, List<Integer>> sequences = new LinkedHashMap<>();
        for (BudgetLine l : budget.lines()) {
            sequences.computeIfAbsent(l.printedCode(), c -> new ArrayList<>()).add(l.sequence());
        }
        List<String> repeatedCodes = sequences.entrySet().stream().filter(en -> en.getValue().size() > 1)
                .map(en -> "%s aparece %d vezes (ordens %s)".formatted(en.getKey(), en.getValue().size(),
                        joinSequences(en.getValue())))
                .toList();
        return new TotalsCheck("CODIGO_REPETIDO", "Nenhum código de linha impresso mais de uma vez",
                repeatedCodes.isEmpty(), repeatedCodes.isEmpty() ? "nenhum código repetido" : String.join("; ",
                        repeatedCodes));
    }

    private static String joinSequences(List<Integer> sequences) {
        List<String> texts = sequences.stream().map(String::valueOf).toList();
        if (texts.size() == 1) {
            return texts.getFirst();
        }
        return String.join(", ", texts.subList(0, texts.size() - 1)) + " e " + texts.getLast();
    }

    /** rate% × base, rounded half up to cents. */
    private static BigDecimal apply(BigDecimal rate, BigDecimal base) {
        return rate.multiply(base).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /** "3,00%" becomes 3.00. Text outside the format: empty. */
    public static Optional<BigDecimal> rate(String text) {
        if (text == null || !PERCENTAGE.matcher(text.trim()).matches()) {
            return Optional.empty();
        }
        String number = text.trim().replace("%", "").replace(".", "").replace(",", ".");
        return Optional.of(new BigDecimal(number));
    }

    private record Group(BudgetLine line, List<BudgetLine> lines) {
        public BigDecimal lineSum() {
            return lines.stream().map(BudgetLine::budgeted).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    /** Total, groups (with the lines that come after each one) and the funds group. */
    private record Structure(BudgetLine total, List<Group> groups, List<BudgetLine> withoutGroup,
            Optional<Group> funds) {

        public static Structure of(Budget budget) {
            BudgetLine total = null;
            List<Group> groups = new ArrayList<>();
            List<BudgetLine> withoutGroup = new ArrayList<>();
            Group current = null;
            for (BudgetLine l : budget.lines()) {
                switch (l.type()) {
                    case TOTAL -> total = total == null ? l : total;
                    case GRUPO -> {
                        current = new Group(l, new ArrayList<>());
                        groups.add(current);
                    }
                    case LINHA -> {
                        if (current == null) {
                            withoutGroup.add(l);
                        } else {
                            current.lines().add(l);
                        }
                    }
                }
            }
            Optional<Group> funds = groups.stream().filter(Structure::isFunds).findFirst();
            return new Structure(total, groups, withoutGroup, funds);
        }

        /** Funds group: the account column (or the description) starts with "Fundos", not with "Subtotal". */
        private static boolean isFunds(Group g) {
            String account = g.line().accountText() == null ? "" : normalize(g.line().accountText());
            return account.startsWith("fundos") || (!account.startsWith("subtotal")
                    && normalize(g.line().description()).startsWith("fundos"));
        }

        public BigDecimal subtotalSumWithoutFunds() {
            return groups.stream().filter(g -> funds.filter(f -> f == g).isEmpty()).map(g -> g.line().budgeted())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public BigDecimal lineSumWithoutFunds() {
            return groups.stream().filter(g -> funds.filter(f -> f == g).isEmpty()).map(Group::lineSum)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
