package br.com.condominioauditoria.rag.parser.budget;

import br.com.condominioauditoria.rag.model.budget.Budget;
import br.com.condominioauditoria.rag.model.budget.BudgetLine;
import br.com.condominioauditoria.rag.model.document.ReadDocument;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Page;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Word;
import br.com.condominioauditoria.rag.model.enums.BudgetLineMark;
import br.com.condominioauditoria.rag.model.enums.BudgetLineType;
import br.com.condominioauditoria.rag.parser.TextLine;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads the "PROPOSTA ORÇAMENTÁRIA" (budget) in the layout of the management company Protest, used in the pilot
 * (ADR 0004, Decision 1).
 *
 * <p>As with the cash flow, the reading uses the position of the words in the reader's output (reader contract v1),
 * without AI. Columns: Item, Identificação (budget account or mark), Descrição, Orçado anterior, Orçado do exercício,
 * "%" and Observações. The limits come from each page's header: amounts are right-aligned and belong to the column
 * where they end ({@code x1}); the start of Descrição is the point where the text of almost every line begins,
 * between the end of the "IDENTIFICAÇÃO DESPESA" title and the "DESCRIÇÃO" title (the titles are centered, so they
 * do not work as a direct limit).
 *
 * <p>The budget is read as it is: the code stays as printed (the repeated 1.3.2 stays on two lines), "%" and
 * Observações stay as text. {@link br.com.condominioauditoria.rag.service.calculator.BudgetCheck} checks the sums.
 */
public final class ProtestBudgetParser {

    public static final String NO_TEXT = "PO sem texto; OCR ainda não disponível";

    private static final Pattern CODE = Pattern.compile("^\\d+(\\.\\d+)*$");
    private static final Pattern FISCAL_YEAR = Pattern.compile("\\d{4}\\s*/\\s*\\d{4}");
    private static final Pattern BUDGETED_LABEL = Pattern.compile("^\\d{4}/\\d{4}$");
    private static final Pattern PERCENTAGE = Pattern.compile("^-?\\d+(\\.\\d{3})*,\\d+%$");
    /** Budget account: starts with a numeric code, e.g. "1682 - Sindicatura Profissional". */
    private static final Pattern ACCOUNT = Pattern.compile("^\\d{3,}\\s*-.*$");
    /** Maximum distance between the end of an amount and the end of the column label. */
    private static final double AMOUNT_TOLERANCE = 8;
    /** Slack to the left of the start of Descrição (the text starts at the same point, varying by tenths). */
    private static final double DESCRIPTION_SLACK = 1.5;

    /**
     * Texts of the account column that are a mark, not an account (RF-03.1.1). Compared without accents and ignoring
     * case, by the start of the text ("Sem valor (R$ 0,00)" is "sem valor").
     */
    private static final Map<String, BudgetLineMark> MARKS = marks();
    /** "Rateio à parte" in Observações also gives the mark (1.4.3 Gás has other text in the account column). */
    private static final String APPORTIONMENT_IN_NOTES = "rateio a parte";

    private static Map<String, BudgetLineMark> marks() {
        Map<String, BudgetLineMark> m = new LinkedHashMap<>();
        m.put("rateio a parte", BudgetLineMark.SEPARATE_APPORTIONMENT);
        m.put("negociada isencao", BudgetLineMark.NEGOTIATED_EXEMPTION);
        m.put("sem valor", BudgetLineMark.NO_AMOUNT);
        m.put("valor fixo (sem referencia)", BudgetLineMark.FIXED_AMOUNT_NO_REFERENCE);
        return m;
    }

    /** Recognizes the layout by the title and the header ("ORÇADO" and "Observações") of the first page. */
    public boolean recognizes(ReadDocument document) {
        if (!"pdf".equals(document.type()) || document.pages().isEmpty()) {
            return false;
        }
        List<TextLine> start = TextLine.group(1, document.pages().getFirst().words()).stream().limit(10).toList();
        String text = start.stream().map(TextLine::text).collect(Collectors.joining("\n"));
        return text.contains("PROPOSTA ORÇAMENTÁRIA")
                && start.stream().anyMatch(l -> l.contains("ORÇADO"))
                && start.stream().anyMatch(l -> l.contains("Observações"));
    }

    /** No page with text: scanned PDF. */
    public static boolean noText(ReadDocument document) {
        return "pdf".equals(document.type())
                && document.pages().stream().allMatch(p -> p.words() == null || p.words().isEmpty());
    }

    public Budget parse(ReadDocument document) {
        if (noText(document)) {
            throw new BudgetReadException(NO_TEXT);
        }
        return new Reading().read(document);
    }

    /** Column limits of a page. */
    public record Columns(double identification, double description, double previousRight, double budgetedRight,
            double percentageRight, double headerEnd, List<String> labels) {

        public boolean inColumn(Word p, double right) {
            return Math.abs(p.x1() - right) <= AMOUNT_TOLERANCE;
        }
    }

    private static final class Reading {
        private String title;
        private List<String> budgetColumns;
        private Columns columns;
        private final List<BudgetLine> lines = new ArrayList<>();

        public Budget read(ReadDocument document) {
            for (Page page : document.pages()) {
                List<TextLine> doc = TextLine.group(page.number(), page.words());
                if (title == null) {
                    doc.stream().filter(l -> l.text().contains("PROPOSTA ORÇAMENTÁRIA")).findFirst()
                            .ifPresent(l -> title = l.text());
                }
                Columns ofPage = header(doc);
                if (ofPage != null) {
                    columns = ofPage;
                    if (budgetColumns == null) {
                        budgetColumns = ofPage.labels();
                    }
                }
                if (columns == null) {
                    throw new BudgetReadException("Cabeçalho da PO não encontrado na pág. " + page.number());
                }
                for (TextLine line : doc) {
                    if (line.top() > columns.headerEnd()) {
                        readLine(line);
                    }
                }
            }
            if (title == null) {
                throw new BudgetReadException("Título \"PROPOSTA ORÇAMENTÁRIA\" não encontrado");
            }
            if (lines.isEmpty()) {
                throw new BudgetReadException("PO sem linhas com código");
            }
            Matcher fiscalYear = FISCAL_YEAR.matcher(title);
            return new Budget(title, fiscalYear.find() ? fiscalYear.group() : "", budgetColumns,
                    List.copyOf(lines));
        }

        /**
         * Header on three lines: "ORÇADO ORÇADO %", "Item IDENTIFICAÇÃO ... Observações" and "2025/2026 2026/2027
         * Orçado".
         */
        private static Columns header(List<TextLine> doc) {
            TextLine titles = doc.stream().filter(l -> l.contains("Item") && l.contains("Observações") && l.contains("DESCRIÇÃO"))
                    .findFirst().orElse(null);
            TextLine labels = doc.stream()
                    .filter(l -> l.words().stream().filter(p -> BUDGETED_LABEL.matcher(p.text()).matches()).count() == 2)
                    .findFirst().orElse(null);
            if (titles == null || labels == null) {
                return null;
            }
            Word identification = word(titles, "IDENTIFICAÇÃO");
            Word description = word(titles, "DESCRIÇÃO");
            List<Word> budgetedAmounts = labels.words().stream().filter(p -> BUDGETED_LABEL.matcher(p.text()).matches())
                    .toList();
            Word percentage = labels.words().stream().filter(p -> p.text().equals("Orçado")).findFirst()
                    .orElseGet(() -> doc.stream().filter(l -> l.contains("%") && l.contains("ORÇADO")).findFirst()
                            .map(l -> word(l, "%"))
                            .orElseThrow(() -> new BudgetReadException("Cabeçalho da PO sem a coluna %")));
            double accountTitleEnd = titles.words().stream()
                    .filter(p -> p.x0() >= identification.x0() && p.x1() < description.x0())
                    .mapToDouble(Word::x1).max().orElse(identification.x1());
            double headerEnd = Math.max(titles.top(), labels.top());
            double descriptionStart = descriptionStart(doc, headerEnd, accountTitleEnd, description.x0());
            return new Columns(identification.x0(), descriptionStart, budgetedAmounts.get(0).x1(),
                    budgetedAmounts.get(1).x1(),
                    percentage.x1(), headerEnd, budgetedAmounts.stream().map(Word::text).toList());
        }

        /**
         * Most frequent point where a word starts between the end of the account title and the Descrição title: that
         * is where the Descrição text starts on every line. With none, the Descrição title applies.
         */
        private static double descriptionStart(List<TextLine> doc, double headerEnd, double from, double until) {
            Map<Long, Long> frequency = doc.stream().filter(l -> l.top() > headerEnd)
                    .flatMap(l -> l.words().stream())
                    .filter(p -> p.x0() >= from && p.x0() < until)
                    .collect(Collectors.groupingBy(p -> Math.round(p.x0()), Collectors.counting()));
            return frequency.entrySet().stream()
                    .max(Map.Entry.<Long, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey((a,
                            b) -> Long.compare(b, a))))
                    .map(e -> (double) e.getKey()).orElse(until);
        }

        private static Word word(TextLine line, String text) {
            return line.words().stream().filter(p -> p.text().equals(text)).findFirst()
                    .orElseThrow(() -> new BudgetReadException(
                            "Cabeçalho da PO sem a coluna " + text + " na pág. " + line.page()));
        }

        private void readLine(TextLine line) {
            Word first = line.first();
            if (!CODE.matcher(first.text()).matches() || first.x1() >= columns.identification()) {
                boolean hasAmount = line.words().stream().anyMatch(p -> BudgetAmount.isAmount(p.text())
                        && (columns.inColumn(p, columns.previousRight()) || columns.inColumn(p,
                                columns.budgetedRight())));
                if (hasAmount) {
                    throw new BudgetReadException("Linha com valor e sem código na pág. " + line.page() + ": "
                            + line.text());
                }
                return; // return; // title, footer or loose text without amounts
            }
            String code = first.text();
            List<String> account = new ArrayList<>();
            List<String> description = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            BigDecimal previous = null;
            BigDecimal budgeted = null;
            String percentage = null;
            for (Word p : splitInDescription(line.words().subList(1, line.words().size()))) {
                String t = p.text();
                if (BudgetAmount.isAmount(t) && columns.inColumn(p, columns.previousRight())) {
                    previous = single(previous, BudgetAmount.fromText(t), code, line);
                } else if (BudgetAmount.isAmount(t) && columns.inColumn(p, columns.budgetedRight())) {
                    budgeted = single(budgeted, BudgetAmount.fromText(t), code, line);
                } else if (p.x0() > columns.budgetedRight()) {
                    if (percentage == null && notes.isEmpty() && PERCENTAGE.matcher(t).matches()
                            && p.x1() <= columns.percentageRight() + AMOUNT_TOLERANCE) {
                        percentage = t;
                    } else {
                        notes.add(t);
                    }
                } else if (p.x0() >= columns.description() - DESCRIPTION_SLACK) {
                    description.add(t);
                } else {
                    account.add(t);
                }
            }
            if (previous == null || budgeted == null) {
                throw new BudgetReadException("Linha %s sem os dois valores orçados na pág. %d: %s"
                        .formatted(code, line.page(), line.text()));
            }
            String accountLabel = String.join(" ", account);
            String notesText = notes.isEmpty() ? null : String.join(" ", notes);
            BudgetLineMark accountMark = mark(accountLabel);
            BudgetLineMark mark = accountMark != null ? accountMark
                    : notesText != null && normalize(notesText).contains(APPORTIONMENT_IN_NOTES)
                            ? BudgetLineMark.SEPARATE_APPORTIONMENT : null;
            String budgetAccount = ACCOUNT.matcher(accountLabel).matches() ? accountLabel : null;
            String accountText = budgetAccount == null && accountMark == null && !accountLabel.isEmpty() ? accountLabel : null;
            lines.add(new BudgetLine(lines.size() + 1, line.page(), type(code, line), code, budgetAccount, accountText,
                    mark, String.join(" ", description), previous, budgeted, percentage, notesText));
        }

        /**
         * The PDF sometimes joins the last word of the account with the first word of Descrição (e.g. 1.8.2,
         * "CORRESPONDENCIAApoio"). The word that crosses the start of Descrição is cut where an uppercase stretch meets
         * a capitalized word, at the point closest to the estimated cut position. Without such a point, the word stays
         * whole in the column where it starts.
         */
        private List<Word> splitInDescription(List<Word> words) {
            double boundary = columns.description();
            List<Word> result = new ArrayList<>();
            for (Word p : words) {
                if (p.x0() < boundary - DESCRIPTION_SLACK && p.x1() > boundary + DESCRIPTION_SLACK) {
                    result.addAll(splitWord(p, boundary));
                } else {
                    result.add(p);
                }
            }
            return result;
        }

        private static BigDecimal single(BigDecimal current, BigDecimal fresh, String code, TextLine line) {
            if (current != null) {
                throw new BudgetReadException("Linha %s com dois valores na mesma coluna na pág. %d: %s"
                        .formatted(code, line.page(), line.text()));
            }
            return fresh;
        }

        private static BudgetLineType type(String code, TextLine line) {
            return switch (code.split("\\.").length) {
                case 1 -> BudgetLineType.TOTAL;
                case 2 -> BudgetLineType.GROUP;
                case 3 -> BudgetLineType.LINE;
                default -> throw new BudgetReadException("Código %s com mais de três níveis na pág. %d"
                        .formatted(code, line.page()));
            };
        }

        private static BudgetLineMark mark(String accountLabel) {
            String normal = normalize(accountLabel);
            return MARKS.entrySet().stream().filter(e -> normal.startsWith(e.getKey())).map(Map.Entry::getValue)
                    .findFirst().orElse(null);
        }
    }

    public static List<Word> splitWord(Word p, double boundary) {
        String t = p.text();
        int n = t.length();
        double width = p.x1() - p.x0();
        double estimated = (boundary - p.x0()) / width * n;
        int cutPoint = -1;
        for (int k = 1; k < n - 1; k++) {
            boolean point = Character.isUpperCase(t.charAt(k - 1)) && Character.isUpperCase(t.charAt(k))
                    && Character.isLowerCase(t.charAt(k + 1));
            if (point && (cutPoint < 0 || Math.abs(k - estimated) < Math.abs(cutPoint - estimated))) {
                cutPoint = k;
            }
        }
        if (cutPoint < 0) {
            return List.of(p);
        }
        double xCut = p.x0() + width * cutPoint / n;
        return List.of(new Word(t.substring(0, cutPoint), p.x0(), xCut, p.top(), p.bottom()),
                new Word(t.substring(cutPoint), Math.max(xCut, boundary), p.x1(), p.top(), p.bottom()));
    }

    /** No accents, lowercase and single spaces. */
    public static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}",
                "").toLowerCase(java.util.Locale.ROOT)
                .replaceAll("\\s+", " ").trim();
    }

    public static class BudgetReadException extends RuntimeException {
        public BudgetReadException(String message) {
            super(message);
        }
    }
}
