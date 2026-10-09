package br.com.condominioauditoria.api.mapper;

import br.com.condominioauditoria.api.dto.response.assistant.ChunkLocationResponse;
import br.com.condominioauditoria.api.dto.response.assistant.DocumentChunkResponse;
import br.com.condominioauditoria.api.dto.response.assistant.DocumentCitationResponse;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.contratos.assistente.v1.Localizacao;
import br.com.condominioauditoria.contratos.assistente.v1.Trecho;
import java.util.UUID;

/** rag chunks (contracts/grpc/assistente/v1) → Assistant API DTOs (contracts/openapi.yaml). */
public final class AssistantMapper {

    private AssistantMapper() {
    }

    public static DocumentChunkResponse toResponse(Trecho chunk) {
        return new DocumentChunkResponse(chunk.getTrechoId(), UUID.fromString(chunk.getArquivoId().strip()),
                chunk.getNomeArquivo(), category(chunk.getCategoria()), toResponse(chunk.getLocalizacao()),
                chunk.getTexto(), chunk.getSha256());
    }

    public static DocumentCitationResponse toCitation(int number, Trecho chunk) {
        DocumentChunkResponse d = toResponse(chunk);
        return new DocumentCitationResponse(number, d.chunkId(), d.fileId(), d.fileName(), d.category(),
                d.location(), d.text(), d.sha256());
    }

    public static ChunkLocationResponse toResponse(Localizacao location) {
        return switch (location.getTipoCase()) {
            case PAGINA -> new ChunkLocationResponse("PAGINA", location.getPagina().getPagina(), null, null, null,
                    null, null, null, "página " + location.getPagina().getPagina());
            case PLANILHA -> {
                var p = location.getPlanilha();
                yield new ChunkLocationResponse("PLANILHA", null, p.getAba(), p.getLinhaInicio(), p.getLinhaFim(),
                        null, null, null, "aba " + p.getAba() + ", "
                                + range("linha", "linhas", p.getLinhaInicio(), p.getLinhaFim()));
            }
            case PARAGRAFOS -> {
                var p = location.getParagrafos();
                String section = p.getSecao().isBlank() ? null : p.getSecao();
                String description = range("parágrafo", "parágrafos", p.getParagrafoInicio(), p.getParagrafoFim());
                yield new ChunkLocationResponse("PARAGRAFOS", null, null, null, null, p.getParagrafoInicio(),
                        p.getParagrafoFim(), section, section == null ? description
                                : "seção " + section + ", " + description);
            }
            case TIPO_NOT_SET -> new ChunkLocationResponse("PAGINA", null, null, null, null, null, null, null,
                    "localização não informada");
        };
    }

    /** The rag's category; outside the list = OUTROS (the contract only has the api's categories). */
    static FileCategory category(String value) {
        try {
            return FileCategory.valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return FileCategory.OUTROS;
        }
    }

    private static String range(String one, String many, int start, int end) {
        return end <= start ? one + " " + start : many + " " + start + " a " + end;
    }
}
