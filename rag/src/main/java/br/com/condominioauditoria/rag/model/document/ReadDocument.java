package br.com.condominioauditoria.rag.model.document;

import java.util.List;

/**
 * Output of the document reader, in the contracts/reader/v2 contract. Only raw content with positions. The
 * Java side does the interpretation.
 */
public record ReadDocument(
        String contractVersion,
        String reader,
        FileInfo file,
        String type,
        List<Page> pages,
        List<Sheet> sheets,
        List<Paragraph> paragraphs) {

    public record FileInfo(
            String name,
            String sha256,
            long sizeBytes) {
    }

    public record Page(
            int number,
            double width,
            double height,
            String method,
            List<Word> words) {
    }

    /** Coordinates in points, with the origin at the top left corner of the page. */
    public record Word(
            String text,
            double x0,
            double x1,
            double top,
            double bottom) {
    }

    public record Sheet(String name, List<Cell> cells) {
    }

    public record Cell(
            int row,
            int column,
            String value,
            String type) {
    }

    public record Paragraph(
            int sequence,
            String text,
            Integer table) {
    }
}
