package br.com.condominioauditoria.api.dto.response.budget;

/** Exported file (PDF or Excel): name, content type and bytes. */
public record ExportedFileResponse(String name, String contentType, byte[] content) {
}
