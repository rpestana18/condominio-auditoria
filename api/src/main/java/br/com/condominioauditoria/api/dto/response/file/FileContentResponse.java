package br.com.condominioauditoria.api.dto.response.file;

import java.io.InputStream;

/** The original file, exactly as uploaded, for download. {@code contentType} may be null. */
public record FileContentResponse(String originalName, String contentType, InputStream content) {
}
