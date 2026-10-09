package br.com.condominioauditoria.api.dto.response.feature;

/** The usage spreadsheet (.xlsx) ready for download. */
public record UsageExportResponse(String fileName, String contentType, byte[] content) {
}
