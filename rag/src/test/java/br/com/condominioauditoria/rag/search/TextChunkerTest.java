package br.com.condominioauditoria.rag.search;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.rag.model.document.ReadDocument;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Cell;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Page;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Paragraph;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Sheet;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Word;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Chunking by location (ADR 0003, Decision 3): never spans pages or tabs. */
public class TextChunkerTest {

    // ---------------------------------------------------------------- PDF

    @Test
    public void pdfOneChunkPerPageWithLinesInReadingOrder() {
        var p1 = page(1, List.of(
                new Word("mundo", 60, 90, 10.5, 20), // mesma linha de "Olá", um pouco mais baixa
                new Word("Olá", 10, 50, 10, 20),
                new Word("Segunda", 10, 60, 30, 40)));
        var p2 = page(2, List.of(new Word("Página", 10, 50, 10, 20), new Word("dois", 55, 80, 10, 20)));

        ChunkedDocument chunked = TextChunker.chunk(pdf(p1, p2));

        assertThat(chunked.pages()).isEqualTo(2);
        assertThat(chunked.chunks()).extracting(Chunk::text).containsExactly("Olá mundo\nSegunda",
                "Página dois");
        assertThat(chunked.chunks()).extracting(Chunk::location)
                .containsExactly(new Location.Page(1), new Location.Page(2));
        assertThat(chunked.chunks()).extracting(Chunk::sequence).containsExactly(1, 2);
    }

    @Test
    public void pdfLongPageBecomesSeveralChunksOfSamePageWithOverlap() {
        var words = new ArrayList<Word>();
        for (int line = 0; line < 200; line++) { // 200 lines of ~50 characters = ~10 thousand characters
            words.add(new Word("linha%03d".formatted(line), 10, 60, line * 12.0, line * 12.0 + 10));
            words.add(new Word("texto de exemplo para a página longa do PDF", 70, 300, line * 12.0,
                    line * 12.0 + 10));
        }
        var shortText = page(4, List.of(new Word("fim", 10, 30, 10, 20)));

        ChunkedDocument chunked = TextChunker.chunk(pdf(page(3, words), shortText));

        List<Chunk> fromPage3 = chunked.chunks().stream()
                .filter(t -> t.location().equals(new Location.Page(3))).toList();
        assertThat(fromPage3).hasSizeGreaterThanOrEqualTo(3);
        assertThat(fromPage3).allSatisfy(t -> assertThat(t.text().length())
                .isLessThanOrEqualTo(TextChunker.MAX_CHARS));
        // Every line of the page appears in some chunk
        String everything = String.join("\n", fromPage3.stream().map(Chunk::text).toList());
        for (int line = 0; line < 200; line++) {
            assertThat(everything).contains("linha%03d ".formatted(line));
        }
        // The next chunk starts by repeating the last lines of the previous one (up to 200 characters)
        List<String> firstLines = List.of(fromPage3.get(0).text().split("\n"));
        List<String> secondLines = List.of(fromPage3.get(1).text().split("\n"));
        int repeatEnd = secondLines.indexOf(firstLines.getLast());
        assertThat(repeatEnd).isBetween(0, 4);
        assertThat(secondLines.subList(0, repeatEnd + 1)).isEqualTo(
                firstLines.subList(firstLines.size() - repeatEnd - 1, firstLines.size()));
        assertThat(String.join("\n", secondLines.subList(0, repeatEnd + 1)).length())
                .isLessThanOrEqualTo(TextChunker.OVERLAP);
        // Page 4 does not mix with page 3
        assertThat(chunked.chunks().getLast().location()).isEqualTo(new Location.Page(4));
        assertThat(chunked.chunks().getLast().text()).isEqualTo("fim");
    }

    @Test
    public void pdfSkipsPageWithoutTextAndNumbersInOrder() {
        var scanned = new Page(1, 595, 842, "sem_texto", List.of());
        var withText = page(2, List.of(new Word("Ata", 10, 30, 10, 20)));

        ChunkedDocument chunked = TextChunker.chunk(pdf(scanned, withText));

        assertThat(chunked.pages()).isEqualTo(2);
        assertThat(chunked.chunks()).singleElement().satisfies(t -> {
            assertThat(t.location()).isEqualTo(new Location.Page(2));
            assertThat(t.sequence()).isEqualTo(1);
        });
    }

    @Test
    public void pdfWithNoTextHasReason() {
        var p1 = new Page(1, 595, 842, "sem_texto", List.of());
        var p2 = new Page(2, 595, 842, "sem_texto", List.of());

        ChunkedDocument chunked = TextChunker.chunk(pdf(p1, p2));

        assertThat(chunked.noText()).isTrue();
        assertThat(chunked.pages()).isEqualTo(2);
        assertThat(chunked.noTextReason()).contains("sem texto extraível").contains("2 páginas");
    }

    @Test
    public void hugeLineWithoutSpacesIsCutWithoutLosingText() {
        String huge = "x".repeat(7000);
        List<String> parts = TextChunker.breakLongLine(huge, 3000);
        assertThat(parts).hasSize(3);
        assertThat(String.join("", parts)).isEqualTo(huge);
    }

    // ---------------------------------------------------------------- Excel

    @Test
    public void excelBlocksOfThirtyRowsRepeatingHeader() {
        var cells = new ArrayList<Cell>();
        cells.add(new Cell(1, 1, "Data", "texto"));
        cells.add(new Cell(1, 2, "Valor", "texto"));
        for (int line = 2; line <= 71; line++) { // 70 data rows
            cells.add(new Cell(line, 2, "%d.00".formatted(line), "numero")); // fora de ordem de coluna
            cells.add(new Cell(line, 1, "2026-09-%02d".formatted(line % 28 + 1), "data"));
        }

        ChunkedDocument chunked = TextChunker.chunk(xlsx(new Sheet("Despesas", cells)));

        assertThat(chunked.pages()).isEqualTo(1);
        assertThat(chunked.chunks()).extracting(Chunk::location).containsExactly(
                new Location.Sheet("Despesas", 2, 31),
                new Location.Sheet("Despesas", 32, 61),
                new Location.Sheet("Despesas", 62, 71));
        assertThat(chunked.chunks()).allSatisfy(t -> assertThat(t.text()).startsWith("Data | Valor\n"));
        assertThat(chunked.chunks().getFirst().text().split("\n")).hasSize(31);
        assertThat(chunked.chunks().getFirst().text()).contains("2026-09-03 | 2.00");
    }

    @Test
    public void excelNeverJoinsTabsAndSkipsEmptyTab() {
        var a = new Sheet("Janeiro", List.of(new Cell(1, 1, "Item", "texto"), new Cell(2, 1, "Água", "texto")));
        var empty = new Sheet("Vazia", List.of(new Cell(1, 1, "  ", "texto")));
        var b = new Sheet("Fevereiro", List.of(new Cell(3, 1, "Só cabeçalho", "texto")));

        ChunkedDocument chunked = TextChunker.chunk(xlsx(a, empty, b));

        assertThat(chunked.pages()).isEqualTo(3);
        assertThat(chunked.chunks()).extracting(Chunk::location).containsExactly(
                new Location.Sheet("Janeiro", 2, 2),
                new Location.Sheet("Fevereiro", 3, 3));
        assertThat(chunked.chunks()).extracting(Chunk::text).containsExactly("Item\nÁgua", "Só cabeçalho");
    }

    @Test
    public void excelWithoutCellsHasNoText() {
        ChunkedDocument chunked = TextChunker.chunk(xlsx(new Sheet("A", List.of())));
        assertThat(chunked.noText()).isTrue();
        assertThat(chunked.noTextReason()).isNotBlank();
    }

    // ---------------------------------------------------------------- Word

    @Test
    public void wordGroupsParagraphsUpToLimitAndSkipsEmpty() {
        var paragraphs = new ArrayList<Paragraph>();
        paragraphs.add(new Paragraph(1, "CONTRACT DE PRESTAÇÃO DE SERVIÇOS", null));
        paragraphs.add(new Paragraph(2, "   ", null));
        for (int i = 3; i <= 12; i++) { // 10 paragraphs of 1000 characters
            paragraphs.add(new Paragraph(i, "p%02d ".formatted(i) + "a".repeat(996), null));
        }

        ChunkedDocument chunked = TextChunker.chunk(docx(paragraphs));

        assertThat(chunked.pages()).isEqualTo(1);
        assertThat(chunked.chunks()).extracting(Chunk::location).containsExactly(
                new Location.Paragraphs(1, 5, ""),
                new Location.Paragraphs(6, 8, ""),
                new Location.Paragraphs(9, 11, ""),
                new Location.Paragraphs(12, 12, ""));
        assertThat(chunked.chunks()).allSatisfy(t -> assertThat(t.text().length())
                .isLessThanOrEqualTo(TextChunker.MAX_CHARS));
        assertThat(chunked.chunks().getFirst().text()).startsWith("CONTRACT DE PRESTAÇÃO DE SERVIÇOS\np03 ");
    }

    @Test
    public void wordLongParagraphBecomesSeveralChunksOfSameParagraph() {
        String longText = ("cláusula " + "b".repeat(40) + " ").repeat(200); // ~10 mil caracteres
        var paragraphs = List.of(new Paragraph(1, "Antes", null), new Paragraph(2, longText, null),
                new Paragraph(3, "Depois", null));

        ChunkedDocument chunked = TextChunker.chunk(docx(paragraphs));

        assertThat(chunked.chunks().getFirst().location()).isEqualTo(new Location.Paragraphs(1, 1, ""));
        assertThat(chunked.chunks().getLast().location()).isEqualTo(new Location.Paragraphs(3, 3, ""));
        List<Chunk> middleChunks = chunked.chunks().subList(1, chunked.chunks().size() - 1);
        assertThat(middleChunks).hasSizeGreaterThanOrEqualTo(3)
                .allSatisfy(t -> assertThat(t.location()).isEqualTo(new Location.Paragraphs(2, 2, "")));
    }

    @Test
    public void sameDocumentAlwaysYieldsSameChunks() {
        var doc = docx(List.of(new Paragraph(1, "Um", null), new Paragraph(2, "Dois", null)));
        assertThat(TextChunker.chunk(doc)).isEqualTo(TextChunker.chunk(doc));
    }

    // ---------------------------------------------------------------- helpers

    private static Page page(int number, List<Word> words) {
        return new Page(number, 595, 842, "texto", words);
    }

    private static ReadDocument pdf(Page... pages) {
        return new ReadDocument("1", "teste", file("a.pdf"), "pdf", List.of(pages), List.of(), List.of());
    }

    private static ReadDocument xlsx(Sheet... sheets) {
        return new ReadDocument("1", "teste", file("a.xlsx"), "xlsx", List.of(), List.of(sheets), List.of());
    }

    private static ReadDocument docx(List<Paragraph> paragraphs) {
        return new ReadDocument("1", "teste", file("a.docx"), "docx", List.of(), List.of(), paragraphs);
    }

    private static ReadDocument.FileInfo file(String name) {
        return new ReadDocument.FileInfo(name, "a".repeat(64), 100);
    }
}
