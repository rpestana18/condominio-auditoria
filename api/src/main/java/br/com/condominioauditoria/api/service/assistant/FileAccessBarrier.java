package br.com.condominioauditoria.api.service.assistant;

import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.contratos.assistente.v1.Trecho;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Second barrier on the way back from the rag (ADR 0003, Decision 5.2, step 3): only chunks of files that exist in the
 * api and belong to the requested condominium remain. The user's access to the condominium was checked before calling
 * the rag; within the condominium, every role sees every file (§4). A file that no longer exists in the api (deleted)
 * is dropped here.
 */
@Component
public class FileAccessBarrier {

    private final SourceFileRepository files;

    public FileAccessBarrier(SourceFileRepository files) {
        this.files = files;
    }

    /** Ids (as text) of the cited files that may appear in the answer. */
    Set<String> visibleIds(UUID condominiumId, Collection<Trecho> chunks) {
        Set<UUID> cited = new HashSet<>();
        for (Trecho t : chunks) {
            uuid(t.getArquivoId()).ifPresent(cited::add);
        }
        if (cited.isEmpty()) {
            return Set.of();
        }
        return files.findByCondominiumIdAndIdIn(condominiumId, cited).stream()
                .map(SourceFile::getId).map(UUID::toString).collect(Collectors.toSet());
    }

    static boolean isAllowed(Trecho t, Set<String> visible) {
        return uuid(t.getArquivoId()).map(UUID::toString).filter(visible::contains).isPresent();
    }

    private static java.util.Optional<UUID> uuid(String value) {
        try {
            return java.util.Optional.of(UUID.fromString(value.strip()));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
