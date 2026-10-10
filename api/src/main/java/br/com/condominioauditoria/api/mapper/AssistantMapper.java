package br.com.condominioauditoria.api.mapper;

import br.com.condominioauditoria.api.dto.response.assistant.ChunkLocationResponse;
import br.com.condominioauditoria.api.dto.response.assistant.DocumentChunkResponse;
import br.com.condominioauditoria.api.dto.response.assistant.DocumentCitationResponse;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.contracts.assistant.v2.ChunkLocation;
import br.com.condominioauditoria.contracts.assistant.v2.IndexedChunk;
import java.util.UUID;

/** rag chunks (contracts/grpc/assistant/v2) → Assistant API DTOs (contracts/openapi.yaml). */
public final class AssistantMapper {

    private AssistantMapper() {
    }

    public static DocumentChunkResponse toResponse(IndexedChunk chunk) {
        return new DocumentChunkResponse(chunk.getChunkId(), UUID.fromString(chunk.getFileId().strip()),
                chunk.getFileName(), category(chunk.getCategory()), toResponse(chunk.getLocation()),
                chunk.getText(), chunk.getSha256());
    }

    public static DocumentCitationResponse toCitation(int number, IndexedChunk chunk) {
        DocumentChunkResponse d = toResponse(chunk);
        return new DocumentCitationResponse(number, d.chunkId(), d.fileId(), d.fileName(), d.category(),
                d.location(), d.text(), d.sha256());
    }

    public static ChunkLocationResponse toResponse(ChunkLocation location) {
        return switch (location.getKindCase()) {
            case PAGE -> new ChunkLocationResponse("PAGE", location.getPage().getPage(), null, null, null,
                    null, null, null, "página " + location.getPage().getPage());
            case SHEET -> {
                var p = location.getSheet();
                yield new ChunkLocationResponse("SHEET", null, p.getTab(), p.getStartRow(), p.getEndRow(),
                        null, null, null, "aba " + p.getTab() + ", "
                                + range("linha", "linhas", p.getStartRow(), p.getEndRow()));
            }
            case PARAGRAPHS -> {
                var p = location.getParagraphs();
                String section = p.getSection().isBlank() ? null : p.getSection();
                String description = range("parágrafo", "parágrafos", p.getParagraphStart(), p.getParagraphEnd());
                yield new ChunkLocationResponse("PARAGRAPHS", null, null, null, null, p.getParagraphStart(),
                        p.getParagraphEnd(), section, section == null ? description
                                : "seção " + section + ", " + description);
            }
            case KIND_NOT_SET -> new ChunkLocationResponse("PAGE", null, null, null, null, null, null, null,
                    "localização não informada");
        };
    }

    /** The rag's category; outside the list = OTHER (the contract only has the api's categories). */
    static FileCategory category(String value) {
        try {
            return FileCategory.valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return FileCategory.OTHER;
        }
    }

    private static String range(String one, String many, int start, int end) {
        return end <= start ? one + " " + start : many + " " + start + " a " + end;
    }
}
