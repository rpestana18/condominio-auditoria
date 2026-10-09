package br.com.condominioauditoria.rag.parser.cashflow;

import br.com.condominioauditoria.rag.model.cashflow.CashFlow;
import br.com.condominioauditoria.rag.model.cashflow.FundPosition;
import br.com.condominioauditoria.rag.model.cashflow.FundSection;
import br.com.condominioauditoria.rag.model.cashflow.LedgerEntry;
import br.com.condominioauditoria.rag.model.document.ReadDocument;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Page;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Word;
import br.com.condominioauditoria.rag.parser.TextLine;
import br.com.condominioauditoria.rag.util.BrazilianMoney;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads the "FLUXO DE CAIXA" report by fund (layout of the management company Protest, used in the pilot).
 *
 * <p>For each fund, the report has: name, column header, SALDO ANTERIOR, entries and TOTAIS. At the end, the POSIÇÃO
 * FINANCEIRA table. Ledger account and memo take several lines; so the text of each column is joined into blocks
 * (separated by vertical space) and each block goes to the entry whose date line it covers. This avoids mixing the memo
 * of one entry with its neighbor's.
 */
public final class CashFlowParser {

    private static final Pattern DATE = Pattern.compile("^\\d{2}/\\d{2}/\\d{4}$");
    private static final Pattern PERIOD = Pattern.compile("PERÍODO DE (\\d{2}/\\d{2}/\\d{4}) À (\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern PROPERTY = Pattern.compile("EMPREENDIMENTO: (.+)$");
    private static final Pattern ACCOUNT = Pattern.compile("^(\\d+)\\.\\s*(.*)$");
    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Left margin where the fund name and the first column of Posição Financeira are. */
    private static final double NAME_MARGIN = 40;
    /** Vertical space that separates the text of one entry from the text of the next. */
    private static final double BLOCK_GAP = 10;

    /** Recognizes the layout by the title and the header of the first page. */
    public boolean recognizes(ReadDocument document) {
        if (!"pdf".equals(document.type()) || document.pages().isEmpty()) {
            return false;
        }
        String start = TextLine.group(1, document.pages().getFirst().words()).stream()
                .limit(8).map(TextLine::text).collect(Collectors.joining("\n"));
        return start.contains("FLUXO DE CAIXA") && start.contains("EMPREENDIMENTO:");
    }

    public CashFlow parse(ReadDocument document) {
        return new Reading().read(document);
    }

    /** Reading state, page by page. */
    private static final class Reading {
        private String property;
        private LocalDate start;
        private LocalDate end;
        private final List<FundSection> sections = new ArrayList<>();
        private final List<FundPosition> position = new ArrayList<>();
        private FundPosition positionTotal;

        private String fund;
        private BigDecimal openingBalance;
        private List<LedgerEntry> entries;
        private CashFlowColumns columns;
        private final List<TextLine> region = new ArrayList<>();
        private boolean inFinancialPosition;
        private int sequence;

        public CashFlow read(ReadDocument document) {
            for (Page page : document.pages()) {
                for (TextLine line : TextLine.group(page.number(), page.words())) {
                    process(line);
                }
                closeRegion();
            }
            if (fund != null) {
                throw new CashFlowReadException("Fundo " + fund + " sem linha TOTAIS");
            }
            if (start == null) {
                throw new CashFlowReadException("Período do relatório não encontrado");
            }
            return new CashFlow(property, start, end, List.copyOf(sections), List.copyOf(position), positionTotal);
        }

        private void process(TextLine line) {
            String text = line.text();
            if (inFinancialPosition) {
                positionLine(line);
                return;
            }
            if (start == null) {
                Matcher period = PERIOD.matcher(text);
                if (period.find()) {
                    start = LocalDate.parse(period.group(1), DD_MM_YYYY);
                    end = LocalDate.parse(period.group(2), DD_MM_YYYY);
                    return;
                }
            }
            Matcher prop = PROPERTY.matcher(text);
            if (property == null && prop.find()) {
                property = prop.group(1).trim();
                return;
            }
            if (text.startsWith("POSIÇÃO FINANCEIRA")) {
                closeRegion();
                inFinancialPosition = true;
                return;
            }
            var header = CashFlowColumns.fromHeader(line);
            if (header.isPresent()) {
                closeRegion();
                columns = header.get();
                return;
            }
            if (isFundName(line)) {
                closeRegion();
                if (fund != null) {
                    throw new CashFlowReadException("Fundo " + fund + " terminou sem linha TOTAIS (pág. " + line.page() + ")");
                }
                fund = text;
                openingBalance = null;
                entries = new ArrayList<>();
                columns = null;
                return;
            }
            if (fund == null || columns == null || isHeaderRest(text)) {
                return; // return; // report title or "CONTA CONTÁBIL" broken into two lines
            }
            if (line.contains("SALDO") && line.contains("ANTERIOR")) {
                openingBalance = amounts(line).getOrDefault(CashFlowColumns.Amount.BALANCE, BrazilianMoney.ZERO);
                return;
            }
            if (line.contains("TOTAIS")) {
                closeRegion();
                closeFund(line);
                return;
            }
            region.add(line);
        }

        private static boolean isHeaderRest(String text) {
            return text.equals("CONTA") || text.equals("CONTÁBIL") || text.equals("CONTA CONTÁBIL");
        }

        private boolean isFundName(TextLine line) {
            Word first = line.first();
            return first.x0() < NAME_MARGIN
                    && !DATE.matcher(first.text()).matches()
                    && line.words().stream().noneMatch(p -> BrazilianMoney.isAmount(p.text()));
        }

        private void closeFund(TextLine totals) {
            if (openingBalance == null) {
                throw new CashFlowReadException("Fundo " + fund + " sem SALDO ANTERIOR");
            }
            List<BigDecimal> numbers = totals.words().stream()
                    .filter(p -> BrazilianMoney.isAmount(p.text())).map(p -> BrazilianMoney.fromText(p.text())).toList();
            if (numbers.size() != 2) {
                throw new CashFlowReadException("Linha TOTAIS do fundo " + fund + " sem crédito e débito: " + totals.text());
            }
            sections.add(new FundSection(fund, openingBalance, List.copyOf(entries), numbers.get(0), numbers.get(1)));
            fund = null;
        }

        /** Joins the text of several lines into blocks and links each block to the date line it covers. */
        private void closeRegion() {
            if (region.isEmpty()) {
                return;
            }
            Map<CashFlowColumns.Text, List<Block>> blocks = new EnumMap<>(CashFlowColumns.Text.class);
            for (CashFlowColumns.Text column : CashFlowColumns.Text.values()) {
                blocks.put(column, columnBlocks(column));
            }
            for (TextLine line : region) {
                if (!DATE.matcher(line.first().text()).matches()) {
                    continue;
                }
                Map<CashFlowColumns.Amount, BigDecimal> amounts = amounts(line);
                if (!amounts.containsKey(CashFlowColumns.Amount.BALANCE)) {
                    throw new CashFlowReadException("Lançamento sem saldo na pág. " + line.page() + ": " + line.text());
                }
                String account = textAtHeight(blocks.get(CashFlowColumns.Text.ACCOUNT), line.top());
                String memo = textAtHeight(blocks.get(CashFlowColumns.Text.MEMO), line.top());
                Matcher m = ACCOUNT.matcher(account);
                String accountCode = m.matches() ? m.group(1) : null;
                String accountName = m.matches() ? m.group(2) : account;
                BigDecimal credit = amounts.getOrDefault(CashFlowColumns.Amount.CREDIT, BrazilianMoney.ZERO);
                BigDecimal debit = amounts.getOrDefault(CashFlowColumns.Amount.DEBIT, BrazilianMoney.ZERO);
                entries.add(new LedgerEntry(
                        line.page(),
                        ++sequence,
                        LocalDate.parse(line.first().text(), DD_MM_YYYY),
                        accountCode,
                        accountName,
                        textAtHeight(blocks.get(CashFlowColumns.Text.CODE), line.top()),
                        memo,
                        credit,
                        debit,
                        amounts.get(CashFlowColumns.Amount.BALANCE),
                        EntryEnricher.enrich(accountName, memo, credit, debit)));
            }
            region.clear();
        }

        private Map<CashFlowColumns.Amount, BigDecimal> amounts(TextLine line) {
            Map<CashFlowColumns.Amount, BigDecimal> amounts = new EnumMap<>(CashFlowColumns.Amount.class);
            for (Word p : line.words()) {
                if (BrazilianMoney.isAmountCell(p.text())) {
                    columns.amountColumn(p).ifPresent(c -> amounts.put(c, BrazilianMoney.fromText(p.text())));
                }
            }
            return amounts;
        }

        private List<Block> columnBlocks(CashFlowColumns.Text column) {
            List<Block> blocks = new ArrayList<>();
            Block current = null;
            for (TextLine line : region) {
                List<Word> words = line.words().stream()
                        .filter(p -> !(BrazilianMoney.isAmountCell(p.text()) && columns.amountColumn(p).isPresent()))
                        .filter(p -> !DATE.matcher(p.text()).matches() || p.x0() > columns.account())
                        .filter(p -> columns.textColumn(p).filter(column::equals).isPresent())
                        .toList();
                if (words.isEmpty()) {
                    continue;
                }
                double top = words.getFirst().top();
                if (current == null || top - current.end > BLOCK_GAP) {
                    current = new Block(top);
                    blocks.add(current);
                }
                current.add(top, words.stream().map(Word::text).collect(Collectors.joining(" ")));
            }
            return blocks;
        }

        private static String textAtHeight(List<Block> blocks, double top) {
            return blocks.stream().filter(b -> b.start - 3 <= top && top <= b.end + 3)
                    .findFirst().map(b -> String.join(" ", b.lines)).orElse("");
        }

        private void positionLine(TextLine line) {
            List<Word> numbers = line.words().stream().filter(p -> BrazilianMoney.isAmount(p.text())).toList();
            if (numbers.size() != 4) {
                return;
            }
            String name = line.words().stream().filter(p -> !BrazilianMoney.isAmount(p.text()))
                    .map(Word::text).collect(Collectors.joining(" "));
            FundPosition p = new FundPosition(name,
                    BrazilianMoney.fromText(numbers.get(0).text()), BrazilianMoney.fromText(numbers.get(1).text()),
                    BrazilianMoney.fromText(numbers.get(2).text()), BrazilianMoney.fromText(numbers.get(3).text()));
            if (name.equals("TOTAL")) {
                positionTotal = p;
            } else {
                position.add(p);
            }
        }
    }

    private static final class Block {
        private final double start;
        private double end;
        private final List<String> lines = new ArrayList<>();

        public Block(double start) {
            this.start = start;
            this.end = start;
        }

        public void add(double top, String text) {
            end = top;
            lines.add(text);
        }
    }

    public static class CashFlowReadException extends RuntimeException {
        public CashFlowReadException(String message) {
            super(message);
        }
    }
}
