package br.com.condominioauditoria.api.dto.response.budget;

import java.time.Instant;

/** The condominium's stored suggestion sheet: the last one loaded (Q51). */
public record StoredSheetResponse(
        String fileName,
        String sha256,
        int lines,
        String loadedBy,
        Instant loadedAt) {
}
