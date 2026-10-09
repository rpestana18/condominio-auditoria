package br.com.condominioauditoria.rag.model.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Output of the document reader (leitor), in the contracts/leitor/v1 contract. Only raw content with positions. The
 * Java side does the interpretation.
 */
public record ReadDocument(
        @JsonProperty("versaoContrato") String contractVersion,
        @JsonProperty("leitor") String reader,
        @JsonProperty("arquivo") FileInfo file,
        @JsonProperty("tipo") String type,
        @JsonProperty("paginas") List<Page> pages,
        @JsonProperty("planilhas") List<Sheet> sheets,
        @JsonProperty("paragrafos") List<Paragraph> paragraphs) {

    public record FileInfo(
            @JsonProperty("nome") String name,
            String sha256,
            @JsonProperty("tamanhoBytes") long sizeBytes) {
    }

    public record Page(
            @JsonProperty("numero") int number,
            @JsonProperty("largura") double width,
            @JsonProperty("altura") double height,
            @JsonProperty("metodo") String method,
            @JsonProperty("palavras") List<Word> words) {
    }

    /** Coordinates in points, with the origin at the top left corner of the page. */
    public record Word(
            @JsonProperty("texto") String text,
            double x0,
            double x1,
            @JsonProperty("topo") double top,
            @JsonProperty("base") double bottom) {
    }

    public record Sheet(@JsonProperty("nome") String name, @JsonProperty("celulas") List<Cell> cells) {
    }

    public record Cell(
            @JsonProperty("linha") int row,
            @JsonProperty("coluna") int column,
            @JsonProperty("valor") String value,
            @JsonProperty("tipo") String type) {
    }

    public record Paragraph(
            @JsonProperty("ordem") int sequence,
            @JsonProperty("texto") String text,
            @JsonProperty("tabela") Integer table) {
    }
}
