package br.com.condominioauditoria.api.exception;

import br.com.condominioauditoria.api.model.file.SourceFile;
import java.util.UUID;

/** The same content (same SHA-256) was already uploaded to this condominium. */
public class DuplicateFileException extends RuntimeException {

    private final UUID existingId;

    public DuplicateFileException(SourceFile existing) {
        super("Este arquivo já foi enviado em " + existing.getUploadedAt() + " (" + existing.getOriginalName() + ")");
        this.existingId = existing.getId();
    }

    public UUID existingId() {
        return existingId;
    }
}
