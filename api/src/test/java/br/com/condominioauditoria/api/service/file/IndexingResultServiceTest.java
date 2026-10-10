package br.com.condominioauditoria.api.service.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.messaging.IndexingResultMessage;
import br.com.condominioauditoria.api.messaging.IndexingResultMessage.Status;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.IndexingStatus;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.service.usage.UsageService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The indexing status only changes with a result of the current request, of the same condominium, in the right order.
 */
class IndexingResultServiceTest {

    private final SourceFileRepository files = mock(SourceFileRepository.class);
    private final UsageService usageRecorder = mock(UsageService.class);
    private final IndexingResultService service = new IndexingResultService(files, usageRecorder);
    private SourceFile file;

    @BeforeEach
    void setUp() {
        file = new SourceFile(UUID.randomUUID(), FileCategory.MINUTES, "ata.pdf", "c/MINUTES/2026/x-ata.pdf", "a".repeat(64), 10,
                "application/pdf", "gestor");
        file.requestIndexing();
        when(files.findById(file.getId())).thenReturn(Optional.of(file));
    }

    @Test
    void newFileStartsWithoutIndexing() {
        var newFile = new SourceFile(UUID.randomUUID(), FileCategory.MINUTES, "ata.pdf", "c/MINUTES/2026/x-ata.pdf", "a".repeat(64), 10,
                "application/pdf", "gestor");
        assertThat(newFile.getIndexingStatus()).isNull();
        assertThat(newFile.getIndexingId()).isNull();
    }

    @Test
    void indexedRecordsUsageOnceEvenWhenRedelivered() {
        service.apply(result(file.getIndexingId(), Status.INDEXING, null, null, null));
        service.apply(result(file.getIndexingId(), Status.INDEXED, null, 12, 15));
        service.apply(result(file.getIndexingId(), Status.INDEXED, null, 12, 15)); // redelivery

        verify(usageRecorder, times(1)).recordIndexing(file.getCondominiumId(), 12, "bge-m3");
    }

    @Test
    void noTextErrorAndDiscardedRecordNoUsage() {
        UUID old = file.getIndexingId();
        file.requestIndexing();
        service.apply(result(old, Status.INDEXED, null, 3, 4));
        service.apply(result(file.getIndexingId(), Status.NO_TEXT, "sem texto", 3, 0));
        service.apply(result(file.getIndexingId(), Status.ERROR, "falhou", null, null));

        verifyNoInteractions(usageRecorder);
    }

    @Test
    void newRequestStartsQueued() {
        assertThat(file.getIndexingStatus()).isEqualTo(IndexingStatus.QUEUED);
        assertThat(file.getIndexingId()).isNotNull();
        assertThat(file.getIndexingAttempts()).isEqualTo(1);
    }

    @Test
    void savesIndexingThenIndexed() {
        assertThat(service.apply(result(file.getIndexingId(), Status.INDEXING, null, null, null))).isTrue();
        assertThat(file.getIndexingStatus()).isEqualTo(IndexingStatus.INDEXING);

        assertThat(service.apply(result(file.getIndexingId(), Status.INDEXED, null, 12, 15))).isTrue();
        assertThat(file.getIndexingStatus()).isEqualTo(IndexingStatus.INDEXED);
        assertThat(file.getIndexingPages()).isEqualTo(12);
        assertThat(file.getIndexingChunks()).isEqualTo(15);
        assertThat(file.getIndexingReason()).isNull();
    }

    @Test
    void noTextKeepsTheReason() {
        service.apply(result(file.getIndexingId(), Status.NO_TEXT, "PDF digitalizado sem texto", 3, 0));

        assertThat(file.getIndexingStatus()).isEqualTo(IndexingStatus.NO_TEXT);
        assertThat(file.getIndexingReason()).isEqualTo("PDF digitalizado sem texto");
    }

    @Test
    void resultFromOldRequestIsDiscarded() {
        UUID old = file.getIndexingId();
        file.requestIndexing(); // reprocessed midway

        assertThat(service.apply(result(old, Status.INDEXED, null, 12, 15))).isFalse();
        assertThat(service.apply(result(old, Status.ERROR, "falhou", null, null))).isFalse();

        assertThat(file.getIndexingStatus()).isEqualTo(IndexingStatus.QUEUED);
        assertThat(file.getIndexingChunks()).isNull();
    }

    @Test
    void newRequestClearsThePreviousResult() {
        service.apply(result(file.getIndexingId(), Status.ERROR, "rag caiu", null, null));
        UUID previous = file.getIndexingId();

        file.requestIndexing();

        assertThat(file.getIndexingId()).isNotEqualTo(previous);
        assertThat(file.getIndexingStatus()).isEqualTo(IndexingStatus.QUEUED);
        assertThat(file.getIndexingReason()).isNull();
    }

    @Test
    void lateIndexingDoesNotUndoTheResult() {
        service.apply(result(file.getIndexingId(), Status.INDEXED, null, 1, 2));
        service.apply(result(file.getIndexingId(), Status.INDEXING, null, null, null));

        assertThat(file.getIndexingStatus()).isEqualTo(IndexingStatus.INDEXED);
    }

    @Test
    void resultFromAnotherCondominiumIsDiscarded() {
        var changed = new IndexingResultMessage(3, file.getIndexingId(), file.getId(), UUID.randomUUID(),
                Status.INDEXED, null, 1, 1, "bge-m3", "1");

        assertThat(service.apply(changed)).isFalse();
        assertThat(file.getIndexingStatus()).isEqualTo(IndexingStatus.QUEUED);
    }

    @Test
    void missingFileIsDiscarded() {
        var other = new IndexingResultMessage(3, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Status.INDEXED, null, 1, 1, "bge-m3", "1");
        assertThat(service.apply(other)).isFalse();
    }

    private IndexingResultMessage result(UUID indexingId, Status status, String reason, Integer pages,
            Integer chunks) {
        return new IndexingResultMessage(3, indexingId, file.getId(), file.getCondominiumId(), status, reason,
                pages, chunks, chunks == null ? null : "bge-m3", chunks == null ? null : "1");
    }
}
