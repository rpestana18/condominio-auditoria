package br.com.condominioauditoria.api.dto.response.assistant;


/** Where a chunk is in its file: page, sheet rows or paragraphs, plus a description for the screen. */
public record ChunkLocationResponse(
        String type,
        Integer page,
        String sheet,
        Integer startRow,
        Integer endRow,
        Integer startParagraph,
        Integer endParagraph,
        String section,
        String description) {
}
