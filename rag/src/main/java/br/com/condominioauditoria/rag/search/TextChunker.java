package br.com.condominioauditoria.rag.search;

import br.com.condominioauditoria.rag.model.document.ReadDocument;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Cell;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Page;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Paragraph;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Sheet;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Word;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Cuts the read document into chunks by location (ADR 0003, Decision 3), so the citation is exact:
 * <ul>
 * <li>PDF: by page; a long page becomes more than one chunk, with a small overlap, all with the same page.</li>
 * <li>Excel: by tab, in blocks of up to 30 rows, repeating the header row at the start of each block.</li>
 * <li>Word: groups of consecutive paragraphs up to the size limit.</li>
 * </ul>
 * Never spans pages or tabs. Deterministic: the same document always yields the same chunks. Changed any rule here?
 * Bump {@link #VERSION}: the api reindexes the files with an old version.
 */
public final class TextChunker {

    /** Version of the chunking rules (versaoIndexador in the contract). */
    public static final String VERSION = "1";

    /** About 800 tokens in Portuguese (approx. 4 characters per token). */
    static final int MAX_CHARS = 3200;

    /** Overlap between chunks of the same page (last lines of the previous chunk). */
    static final int OVERLAP = 200;

    static final int ROWS_PER_BLOCK = 30;

    /** Maximum height difference (points) for two PDF words to be on the same line. */
    private static final double LINE_TOLERANCE = 2.0;

    private TextChunker() {
    }

    public static ChunkedDocument chunk(ReadDocument document) {
        return switch (document.type()) {
            case "pdf" -> chunkPdf(document.pages());
            case "xlsx" -> chunkSheets(document.sheets());
            case "docx" -> chunkParagraphs(document.paragraphs());
            default -> throw new IllegalArgumentException("Tipo de documento não suportado: " + document.type());
        };
    }

    // ---------------------------------------------------------------- PDF

    private static ChunkedDocument chunkPdf(List<Page> pages) {
        var chunks = new Chunks();
        for (Page page : pages) {
            if ("sem_texto".equals(page.method()) || page.words().isEmpty()) {
                continue;
            }
            var local = new Location.Page(page.number());
            for (String text : split(pageLines(page.words()))) {
                chunks.add(local, text);
            }
        }
        String reason = chunks.list.isEmpty()
                ? pages.isEmpty() ? "PDF sem páginas"
                        : "PDF sem texto extraível (provavelmente digitalizado); nenhuma das " + pages.size()
                                + (pages.size() == 1 ? " página tem texto" : " páginas tem texto")
                : null;
        return new ChunkedDocument(pages.size(), chunks.list, reason);
    }

    /** Groups the words by height and returns the text of each line, top to bottom. */
    public static List<String> pageLines(List<Word> words) {
        List<Word> sortedLines = words.stream().sorted(Comparator.comparingDouble(Word::top)).toList();
        List<String> lines = new ArrayList<>();
        List<Word> current = new ArrayList<>();
        double currentTop = Double.NaN;
        for (Word p : sortedLines) {
            if (!current.isEmpty() && p.top() - currentTop > LINE_TOLERANCE) {
                lines.add(lineText(current));
                current = new ArrayList<>();
            }
            if (current.isEmpty()) {
                currentTop = p.top();
            }
            current.add(p);
        }
        if (!current.isEmpty()) {
            lines.add(lineText(current));
        }
        return lines.stream().filter(l -> !l.isBlank()).toList();
    }

    private static String lineText(List<Word> words) {
        return words.stream().sorted(Comparator.comparingDouble(Word::x0)).map(Word::text)
                .collect(Collectors.joining(" ")).strip();
    }

    /**
     * Joins the lines into texts of up to {@link #MAX_CHARS}. Each new text starts by repeating the last lines of the
     * previous one (up to {@link #OVERLAP} characters). A line longer than the limit is broken at the words.
     */
    public static List<String> split(List<String> lines) {
        List<String> pieces = new ArrayList<>();
        for (String line : lines) {
            pieces.addAll(breakLongLine(line, MAX_CHARS - OVERLAP - 1));
        }
        List<String> texts = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int size = 0;
        int overlapping = 0; // how many lines at the start of "current" came from the previous text
        for (String piece : pieces) {
            int increment = piece.length() + (current.isEmpty() ? 0 : 1);
            if (current.size() > overlapping && size + increment > MAX_CHARS) {
                texts.add(String.join("\n", current));
                current = overlap(current);
                overlapping = current.size();
                size = current.isEmpty() ? 0 : String.join("\n", current).length();
                increment = piece.length() + (current.isEmpty() ? 0 : 1);
            }
            current.add(piece);
            size += increment;
        }
        if (current.size() > overlapping) {
            texts.add(String.join("\n", current));
        }
        return texts;
    }

    private static List<String> overlap(List<String> lines) {
        List<String> end = new ArrayList<>();
        int size = 0;
        for (int i = lines.size() - 1; i >= 0; i--) {
            int fresh = size + lines.get(i).length() + (end.isEmpty() ? 0 : 1);
            if (fresh > OVERLAP) {
                break;
            }
            end.addFirst(lines.get(i));
            size = fresh;
        }
        return end;
    }

    public static List<String> breakLongLine(String line, int limit) {
        if (line.length() <= limit) {
            return List.of(line);
        }
        List<String> parts = new ArrayList<>();
        var current = new StringBuilder();
        for (String word : line.split("\\s+")) {
            while (word.length() > limit) { // huge word (e.g. a sequence without spaces): hard cut
                if (!current.isEmpty()) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                parts.add(word.substring(0, limit));
                word = word.substring(limit);
            }
            if (!current.isEmpty() && current.length() + 1 + word.length() > limit) {
                parts.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(word);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    // ---------------------------------------------------------------- Excel

    private static ChunkedDocument chunkSheets(List<Sheet> sheets) {
        var chunks = new Chunks();
        for (Sheet sheet : sheets) {
            Map<Integer, String> lines = sheetRows(sheet.cells());
            if (lines.isEmpty()) {
                continue;
            }
            var iterator = lines.entrySet().iterator();
            var header = iterator.next();
            if (!iterator.hasNext()) { // tab with a single row
                chunks.add(new Location.Sheet(sheet.name(), header.getKey(), header.getKey()),
                        header.getValue());
                continue;
            }
            List<Map.Entry<Integer, String>> block = new ArrayList<>();
            int size = header.getValue().length();
            while (iterator.hasNext()) {
                var line = iterator.next();
                if (!block.isEmpty() && (block.size() == ROWS_PER_BLOCK
                        || size + 1 + line.getValue().length() > MAX_CHARS)) {
                    closeBlock(chunks, sheet.name(), header.getValue(), block);
                    block = new ArrayList<>();
                    size = header.getValue().length();
                }
                block.add(line);
                size += 1 + line.getValue().length();
            }
            closeBlock(chunks, sheet.name(), header.getValue(), block);
        }
        String reason = chunks.list.isEmpty() ? "Planilha sem células preenchidas" : null;
        return new ChunkedDocument(sheets.size(), chunks.list, reason);
    }

    /**
     * Text of each row of the tab (cells in column order, separated by " | "), by row number. The first row with
     * content is treated as the header.
     */
    public static Map<Integer, String> sheetRows(List<Cell> cells) {
        Map<Integer, List<Cell>> byLine = new TreeMap<>();
        for (Cell c : cells) {
            if (c.value() != null && !c.value().isBlank()) {
                byLine.computeIfAbsent(c.row(), l -> new ArrayList<>()).add(c);
            }
        }
        Map<Integer, String> lines = new TreeMap<>();
        byLine.forEach((number, lineText) -> lines.put(number, lineText.stream()
                .sorted(Comparator.comparingInt(Cell::column)).map(c -> c.value().strip())
                .collect(Collectors.joining(" | "))));
        return lines;
    }

    /** The header goes into the text of every block; the location cites only the block's data rows. */
    private static void closeBlock(Chunks chunks, String tab, String header,
            List<Map.Entry<Integer, String>> block) {
        if (block.isEmpty()) {
            return;
        }
        String text = header + "\n" + block.stream().map(Map.Entry::getValue).collect(Collectors.joining("\n"));
        chunks.add(new Location.Sheet(tab, block.getFirst().getKey(), block.getLast().getKey()), text);
    }

    // ---------------------------------------------------------------- Word

    private static ChunkedDocument chunkParagraphs(List<Paragraph> paragraphs) {
        var chunks = new Chunks();
        List<Paragraph> group = new ArrayList<>();
        int size = 0;
        for (Paragraph p : paragraphs) {
            String text = p.text() == null ? "" : p.text().strip();
            if (text.isEmpty()) {
                continue;
            }
            // A long paragraph on its own becomes several chunks with the same location
            if (text.length() > MAX_CHARS) {
                closeGroup(chunks, group);
                group = new ArrayList<>();
                size = 0;
                for (String part : split(breakLongLine(text, MAX_CHARS - OVERLAP - 1))) {
                    chunks.add(new Location.Paragraphs(p.sequence(), p.sequence(), ""), part);
                }
                continue;
            }
            if (!group.isEmpty() && size + 1 + text.length() > MAX_CHARS) {
                closeGroup(chunks, group);
                group = new ArrayList<>();
                size = 0;
            }
            group.add(new Paragraph(p.sequence(), text, p.table()));
            size += (size == 0 ? 0 : 1) + text.length();
        }
        closeGroup(chunks, group);
        String reason = chunks.list.isEmpty() ? "Documento Word sem texto" : null;
        return new ChunkedDocument(1, chunks.list, reason);
    }

    private static void closeGroup(Chunks chunks, List<Paragraph> group) {
        if (group.isEmpty()) {
            return;
        }
        String text = group.stream().map(Paragraph::text).collect(Collectors.joining("\n"));
        chunks.add(new Location.Paragraphs(group.getFirst().sequence(), group.getLast().sequence(), ""), text);
    }

    /** Numbers the chunks in document order. */
    private static final class Chunks {
        private final List<Chunk> list = new ArrayList<>();

        public void add(Location local, String text) {
            list.add(new Chunk(list.size() + 1, local, text));
        }
    }
}
