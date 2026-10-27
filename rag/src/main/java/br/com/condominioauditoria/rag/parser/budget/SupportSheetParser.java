package br.com.condominioauditoria.rag.parser.budget;

import br.com.condominioauditoria.rag.model.TotalsCheck;
import br.com.condominioauditoria.rag.model.budget.BudgetLineOrigin;
import br.com.condominioauditoria.rag.model.budget.BudgetLineOrigins;
import br.com.condominioauditoria.rag.model.document.ReadDocument;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Cell;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Sheet;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the support sheet of the budget (.xlsx; RF-13.13; ADR 0009, Decision 3 B): where the amount of each line came
 * from. The reader's contract (v2) gives only the values of the cells, never the formulas, so the method is
 * <b>deduced from the numbers</b>, with a tolerance of R$ 0,01, without AI:
 * <ol>
 * <li>the base of the line (the "realizado" column) is the sum of the averages of the line's accounts in one of the
 * average columns of the expenses sheet ("MÉDIA AGO/25-ABR/26", "MÉDIA JAN/26-ABR/26", in sheet order) → the line has
 * an average with that period; otherwise {@code NO_AVERAGE};</li>
 * <li>final = base → {@code EQUAL_TO_AVERAGE};</li>
 * <li>final = base × (1 + index), for an index of the index sheet → {@code AVERAGE_TIMES_INDEX}. More than one index
 * with the same result: the one cited in Observações wins, otherwise the first of the sheet; when the applied value is
 * also the value of another index, {@code indexNote} says so (O4: "rótulo IGP-M, valor aplicado 4,44% (igual ao IPCA
 * ACUMULADO)"). An index marked "(previsão)" in the sheet is {@code indexForecast};</li>
 * <li>any other final amount → {@code TYPED}, with Observações as the reason (O8).</li>
 * </ol>
 * Known limit of the deduction: a typed base that happens to equal an average is taken as that average (1.3.23 in the
 * pilot); the Admin corrects the origin (RF-13.13).
 *
 * <p>The columns are found by the header, never by the position: Item, Identificação despesa (the account), Descrição,
 * Realizado (the base), the most recent Orçado (the final amount) and Observações. Lines: the rows of the groups 1.1 to
 * 1.8 (a group code has one dot; the funds group is skipped), in order; an item the spreadsheet turned into a date
 * stays as read. Nothing here is a business rule of the api: the sheet is only matched to the budget by the api.
 */
public final class SupportSheetParser {

    public static final String SUPPORT_SHEET_LINES = "SUPPORT_SHEET_LINES";

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    private static final Pattern GROUP_CODE = Pattern.compile("^\\d+\\.\\d+$");
    private static final Pattern ACCOUNT_CODE = Pattern.compile("(?:^|\\+)\\s*(\\d{3,})\\s*-");
    private static final Pattern YEARS = Pattern.compile("(\\d{4})\\s*/\\s*(\\d{4})");
    /** "media ago/25-abr/26" (already without accents and in lowercase). */
    private static final Pattern AVERAGE = Pattern.compile(
            "media\\s+([a-z]{3})\\s*/\\s*(\\d{2})\\s*-\\s*([a-z]{3})\\s*/\\s*(\\d{2})");
    private static final List<String> MONTHS = List.of("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set",
            "out", "nov", "dez");
    private static final int HEADER_ROWS = 20;

    /** Recognizes a workbook with the budget sheet (header with Item, Descrição, Orçado and Observações). */
    public boolean recognizes(ReadDocument document) {
        return "xlsx".equals(document.type()) && document.sheets() != null
                && document.sheets().stream().anyMatch(s -> budgetHeader(s).isPresent());
    }

    public BudgetLineOrigins parse(ReadDocument document) {
        Sheet budgetSheet = document.sheets().stream().filter(s -> budgetHeader(s).isPresent()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Planilha sem a aba da PO"));
        Header h = budgetHeader(budgetSheet).orElseThrow();
        Expenses expenses = document.sheets().stream().filter(s -> s != budgetSheet).map(SupportSheetParser::expenses)
                .flatMap(Optional::stream).findFirst().orElse(null);
        Indexes indexes = document.sheets().stream().filter(s -> s != budgetSheet).map(SupportSheetParser::indexes)
                .flatMap(Optional::stream).findFirst().orElse(null);

        Map<Integer, Map<Integer, Cell>> grid = grid(budgetSheet);
        List<BudgetLineOrigin> lines = new ArrayList<>();
        boolean inExpenseGroup = false;
        for (var row : grid.entrySet()) {
            if (row.getKey() <= h.row()) {
                continue;
            }
            Map<Integer, Cell> cells = row.getValue();
            String item = text(cells, h.item());
            String account = text(cells, h.account());
            String description = text(cells, h.description());
            if (item != null && GROUP_CODE.matcher(item).matches()) {
                inExpenseGroup = !normalize(description).contains("fundo") && !normalize(account).contains("fundo");
                continue;
            }
            if (!inExpenseGroup || (account == null && description == null)) {
                continue;
            }
            lines.add(line(lines.size() + 1, row.getKey(), item, account, description, number(cells, h.base()),
                    number(cells, h.fin()), text(cells, h.notes()), expenses, indexes));
        }
        return new BudgetLineOrigins(budgetSheet.name(), expenses == null ? null : expenses.sheet(),
                indexes == null ? null : indexes.sheet(), List.copyOf(lines));
    }

    /** Checks of the reading: the number of expense lines read and how many have an average. */
    public static List<TotalsCheck> check(BudgetLineOrigins origins) {
        long withAverage = origins.lines().stream().filter(l -> l.averageFrom() != null).count();
        return List.of(new TotalsCheck(SUPPORT_SHEET_LINES, "Linhas de despesa da planilha de apoio",
                !origins.lines().isEmpty(), origins.lines().size() + " linhas lidas, " + withAverage
                        + " com média de conta"));
    }

    private static BudgetLineOrigin line(int sequence, int row, String item, String account, String description,
            BigDecimal base, BigDecimal fin, String notes, Expenses expenses, Indexes indexes) {
        List<String> accounts = accountCodes(account);
        BigDecimal finalAmount = money(fin == null ? BigDecimal.ZERO : fin);
        BigDecimal baseAmount = base == null ? null : money(base);
        Average average = expenses == null || base == null ? null : expenses.match(accounts, base);
        if (average == null) {
            return new BudgetLineOrigin(sequence, row, item, account, description == null ? "" : description,
                    accounts, null, null, null, baseAmount, "NO_AVERAGE", null, null, false, null, finalAmount,
                    notes);
        }
        String method;
        Index chosen = null;
        String note = null;
        if (close(money(base), finalAmount)) {
            method = "EQUAL_TO_AVERAGE";
        } else {
            List<Index> candidates = indexes == null ? List.of() : indexes.list().stream()
                    .filter(i -> i.rate().signum() != 0
                            && close(money(base.multiply(BigDecimal.ONE.add(i.rate()))), finalAmount))
                    .toList();
            if (candidates.isEmpty()) {
                method = "TYPED";
            } else {
                method = "AVERAGE_TIMES_INDEX";
                String n = normalize(notes);
                chosen = candidates.stream().filter(i -> n.contains(normalize(i.name()))).findFirst()
                        .or(() -> candidates.stream().filter(i -> cites(n, i.name())).findFirst())
                        .orElse(candidates.getFirst());
                Index c = chosen;
                List<Index> same = indexes.list().stream().filter(i -> i != c && i.rate().compareTo(c.rate()) == 0)
                        .toList();
                if (!same.isEmpty()) {
                    boolean cited = cites(n, c.name());
                    note = (cited ? "rótulo " + c.name() + ", valor aplicado " : "valor aplicado ") + percent(c.rate())
                            + " (igual ao " + String.join(" e ao ", same.stream().map(Index::name).toList()) + ")";
                }
            }
        }
        return new BudgetLineOrigin(sequence, row, item, account, description == null ? "" : description, accounts,
                average.from().toString(), average.to().toString(), average.amount(), baseAmount, method,
                chosen == null ? null : chosen.name(), chosen == null ? null : chosen.rate(),
                chosen != null && chosen.forecast(), note, finalAmount, notes);
    }

    /** A word of the index name (4 letters or more) in Observações, or the whole name. */
    private static boolean cites(String notes, String name) {
        String n = normalize(name);
        if (notes.contains(n)) {
            return true;
        }
        for (String w : n.split("[^a-z0-9]+")) {
            if (w.length() >= 4 && notes.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static String percent(BigDecimal rate) {
        return rate.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString().replace('.', ',') + "%";
    }

    static List<String> accountCodes(String account) {
        if (account == null) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        Matcher m = ACCOUNT_CODE.matcher(account);
        while (m.find()) {
            codes.add(m.group(1));
        }
        return List.copyOf(codes);
    }

    private record Header(int row, int item, int account, int description, int base, int fin, int notes) {
    }

    private static Optional<Header> budgetHeader(Sheet sheet) {
        for (var row : grid(sheet).entrySet()) {
            if (row.getKey() > HEADER_ROWS) {
                break;
            }
            int item = -1, account = -1, description = -1, base = -1, fin = -1, notes = -1;
            int latestYear = -1;
            for (Cell c : row.getValue().values()) {
                String t = normalize(c.value());
                if (t.equals("item")) {
                    item = c.column();
                } else if (t.contains("identifica")) {
                    account = c.column();
                } else if (t.startsWith("descri")) {
                    description = c.column();
                } else if (t.startsWith("realizado") && base < 0) {
                    base = c.column();
                } else if (t.startsWith("orcado")) {
                    Matcher y = YEARS.matcher(t);
                    int year = y.find() ? Integer.parseInt(y.group(2)) : 0;
                    if (year >= latestYear) {
                        latestYear = year;
                        fin = c.column();
                    }
                } else if (t.startsWith("observa")) {
                    notes = c.column();
                }
            }
            if (item > 0 && description > 0 && base > 0 && fin > 0 && notes > 0) {
                return Optional.of(new Header(row.getKey(), item, account, description, base, fin, notes));
            }
        }
        return Optional.empty();
    }

    private record Average(YearMonth from, YearMonth to, BigDecimal amount) {
    }

    /** Expenses sheet: the average columns and the amount of each account in each of them. */
    private record Expenses(String sheet, List<Integer> columns, Map<Integer, YearMonth[]> periods,
            Map<String, Map<Integer, BigDecimal>> byAccount) {

        Average match(List<String> accounts, BigDecimal base) {
            if (accounts.isEmpty() || !byAccount.keySet().containsAll(accounts)) {
                return null;
            }
            for (int column : columns) {
                BigDecimal sum = BigDecimal.ZERO;
                boolean all = true;
                for (String a : accounts) {
                    BigDecimal v = byAccount.get(a).get(column);
                    if (v == null) {
                        all = false;
                        break;
                    }
                    sum = sum.add(v);
                }
                if (all && close(money(sum), money(base))) {
                    YearMonth[] p = periods.get(column);
                    return new Average(p[0], p[1], money(sum));
                }
            }
            return null;
        }
    }

    private static Optional<Expenses> expenses(Sheet sheet) {
        Map<Integer, Map<Integer, Cell>> grid = grid(sheet);
        for (var row : grid.entrySet()) {
            if (row.getKey() > HEADER_ROWS) {
                break;
            }
            List<Integer> columns = new ArrayList<>();
            Map<Integer, YearMonth[]> periods = new LinkedHashMap<>();
            for (Cell c : row.getValue().values()) {
                Matcher m = AVERAGE.matcher(normalize(c.value()));
                if (m.find()) {
                    YearMonth from = month(m.group(1), m.group(2));
                    YearMonth to = month(m.group(3), m.group(4));
                    if (from != null && to != null) {
                        columns.add(c.column());
                        periods.put(c.column(), new YearMonth[] {from, to});
                    }
                }
            }
            if (columns.isEmpty()) {
                continue;
            }
            Map<String, Map<Integer, BigDecimal>> byAccount = new LinkedHashMap<>();
            for (var r : grid.entrySet()) {
                if (r.getKey() <= row.getKey()) {
                    continue;
                }
                Cell first = r.getValue().values().stream().findFirst().orElse(null);
                List<String> codes = first == null ? List.of() : accountCodes(first.value());
                if (codes.size() != 1 || byAccount.containsKey(codes.getFirst())) {
                    continue;
                }
                Map<Integer, BigDecimal> values = new LinkedHashMap<>();
                for (int col : columns) {
                    BigDecimal v = number(r.getValue(), col);
                    if (v != null) {
                        values.put(col, v);
                    }
                }
                byAccount.put(codes.getFirst(), values);
            }
            return Optional.of(new Expenses(sheet.name(), List.copyOf(columns), periods, byAccount));
        }
        return Optional.empty();
    }

    private record Index(String name, BigDecimal rate, boolean forecast) {
    }

    private record Indexes(String sheet, List<Index> list) {
    }

    /** Index sheet: header with "TAXA" and "PERCENTUAL"; the cell after the rate may say "(previsão)". */
    private static Optional<Indexes> indexes(Sheet sheet) {
        Map<Integer, Map<Integer, Cell>> grid = grid(sheet);
        for (var row : grid.entrySet()) {
            if (row.getKey() > HEADER_ROWS) {
                break;
            }
            int name = -1, rate = -1;
            for (Cell c : row.getValue().values()) {
                String t = normalize(c.value());
                if (t.equals("taxa")) {
                    name = c.column();
                } else if (t.equals("percentual")) {
                    rate = c.column();
                }
            }
            if (name < 0 || rate < 0) {
                continue;
            }
            List<Index> list = new ArrayList<>();
            for (var r : grid.entrySet()) {
                if (r.getKey() <= row.getKey()) {
                    continue;
                }
                String n = text(r.getValue(), name);
                BigDecimal v = number(r.getValue(), rate);
                if (n == null || v == null) {
                    continue;
                }
                String after = text(r.getValue(), rate + 1);
                // The contract's rate: a decimal fraction with 1 to 6 decimals
                BigDecimal applied = v.scale() < 1 ? v.setScale(1)
                        : v.scale() > 6 ? v.setScale(6, RoundingMode.HALF_UP) : v;
                list.add(new Index(n, applied, normalize(after).contains("previs")));
            }
            return Optional.of(new Indexes(sheet.name(), List.copyOf(list)));
        }
        return Optional.empty();
    }

    private static YearMonth month(String name, String year) {
        int m = MONTHS.indexOf(name);
        return m < 0 ? null : YearMonth.of(2000 + Integer.parseInt(year), m + 1);
    }

    private static Map<Integer, Map<Integer, Cell>> grid(Sheet sheet) {
        Map<Integer, Map<Integer, Cell>> grid = new TreeMap<>();
        if (sheet.cells() != null) {
            sheet.cells().forEach(c -> grid.computeIfAbsent(c.row(), k -> new TreeMap<>()).put(c.column(), c));
        }
        return grid;
    }

    private static String text(Map<Integer, Cell> cells, int column) {
        Cell c = column < 0 ? null : cells.get(column);
        if (c == null || c.value() == null || c.value().isBlank()) {
            return null;
        }
        return c.value().trim();
    }

    private static BigDecimal number(Map<Integer, Cell> cells, int column) {
        Cell c = column < 0 ? null : cells.get(column);
        if (c == null || !"number".equals(c.type()) || c.value() == null) {
            return null;
        }
        try {
            return new BigDecimal(c.value().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean close(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(TOLERANCE) <= 0;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .trim();
    }
}
