package br.com.condominioauditoria.api.service.file;

import br.com.condominioauditoria.api.messaging.IndexingResultMessage;
import br.com.condominioauditoria.api.model.enums.IndexingStatus;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.modulo.RegistroUso;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves the indexing status reported by the rag. A result from an old request (another indexingId) is discarded: the
 * user may have reprocessed the file midway. It is also discarded when the condominium does not match the file's (a
 * mixed-up message never touches a file of another condominium).
 *
 * Every file that becomes Indexed creates an "indexacao" usage record (RF-09.7) with 1 file and the pages read. A
 * repeated INDEXADO of the same request (queue redelivery) does not count again.
 */
@Service
public class IndexingResultService {

    private static final Logger log = LoggerFactory.getLogger(IndexingResultService.class);

    private final SourceFileRepository files;
    private final RegistroUso usageRecorder;

    IndexingResultService(SourceFileRepository files, RegistroUso usageRecorder) {
        this.files = files;
        this.usageRecorder = usageRecorder;
    }

    /** Returns true if the result was applied; false if it was discarded. */
    @Transactional
    public boolean apply(IndexingResultMessage result) {
        SourceFile file = files.findById(result.fileId()).orElse(null);
        if (file == null || !file.isCurrentIndexing(result.indexingId())
                || !file.getCondominiumId().equals(result.condominiumId())) {
            log.info("Resultado de indexação descartado: arquivo {} foi apagado ou reindexado depois deste pedido ({})",
                    result.fileId(), result.indexingId());
            return false;
        }
        boolean alreadyIndexed = file.getIndexingStatus() == IndexingStatus.INDEXADO;
        switch (result.status()) {
            case INDEXANDO -> file.startIndexing();
            case INDEXADO -> file.completeIndexing(IndexingStatus.INDEXADO, null, result.pages(),
                    result.chunks());
            case SEM_TEXTO -> file.completeIndexing(IndexingStatus.SEM_TEXTO, result.reason(),
                    result.pages(), result.chunks());
            case RETIRADO -> file.completeIndexing(IndexingStatus.RETIRADO, null, result.pages(),
                    result.chunks());
            case ERRO -> file.completeIndexing(IndexingStatus.ERRO, result.reason(), result.pages(),
                    result.chunks());
        }
        if (result.status() == IndexingResultMessage.Status.INDEXADO && !alreadyIndexed) {
            usageRecorder.indexacao(file.getCondominiumId(), result.pages(), result.embeddingModel());
        }
        if (result.status() != IndexingResultMessage.Status.INDEXANDO) {
            log.info("Indexação de {}: {} ({} trechos)", file.getOriginalName(), result.status(),
                    result.chunks());
        }
        return true;
    }
}
