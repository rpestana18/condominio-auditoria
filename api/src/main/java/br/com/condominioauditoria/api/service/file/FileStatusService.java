package br.com.condominioauditoria.api.service.file;

import br.com.condominioauditoria.api.model.enums.FileStatus;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Progress reported by the rag. A message from an old read (another processingId) is ignored: the user may have asked
 * for a reprocess midway.
 */
@Service
public class FileStatusService {

    private final SourceFileRepository files;

    FileStatusService(SourceFileRepository files) {
        this.files = files;
    }

    @Transactional
    public void processing(UUID fileId, UUID processingId) {
        files.findById(fileId)
                .filter(a -> a.isCurrentProcessing(processingId))
                .filter(a -> a.getStatus() == FileStatus.PENDING)
                .ifPresent(a -> a.startProcessing());
    }

    @Transactional
    public void failed(UUID fileId, UUID processingId, String reason) {
        files.findById(fileId)
                .filter(a -> a.isCurrentProcessing(processingId))
                .ifPresent(a -> a.fail(reason));
    }
}
