package br.com.condominioauditoria.api.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.event.FeatureChanged;
import br.com.condominioauditoria.api.event.FilesIndexRequested;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.IndexingStatus;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** Enabling the Assistant queues every file for indexing (RF-10.4); disabling touches nothing (RF-10.5). */
class ReindexOnAssistantEnabledListenerTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();

    private final SourceFileRepository files = mock(SourceFileRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ReindexOnAssistantEnabledListener listener = new ReindexOnAssistantEnabledListener(files, events);

    @Test
    void enablingRequestsIndexingOfAllCondominiumFiles() {
        List<SourceFile> forty = IntStream.range(0, 40).mapToObj(i -> file()).toList();
        SourceFile alreadyIndexed = forty.getFirst();
        alreadyIndexed.requestIndexing();
        alreadyIndexed.completeIndexing(IndexingStatus.INDEXADO, null, 3, 5);
        UUID oldRequest = alreadyIndexed.getIndexingId();
        when(files.findByCondominiumIdOrderByUploadedAtDesc(CONDOMINIUM)).thenReturn(forty);

        listener.onFeatureChanged(new FeatureChanged(CONDOMINIUM, FeatureService.ASSISTANT, true, "admin"));

        assertThat(forty).allSatisfy(a -> {
            assertThat(a.getIndexingStatus()).isEqualTo(IndexingStatus.NA_FILA);
            assertThat(a.getIndexingId()).isNotNull();
        });
        assertThat(alreadyIndexed.getIndexingId()).isNotEqualTo(oldRequest);
        var batch = ArgumentCaptor.forClass(FilesIndexRequested.class);
        verify(events).publishEvent(batch.capture());
        assertThat(batch.getValue().condominiumId()).isEqualTo(CONDOMINIUM);
        assertThat(batch.getValue().fileIds()).containsExactlyElementsOf(forty.stream().map(SourceFile::getId).toList());
    }

    @Test
    void disablingTouchesNeitherIndexNorQueue() {
        listener.onFeatureChanged(new FeatureChanged(CONDOMINIUM, FeatureService.ASSISTANT, false, "admin"));

        verifyNoInteractions(files, events);
    }

    @Test
    void condominiumWithoutFilesPublishesNothing() {
        when(files.findByCondominiumIdOrderByUploadedAtDesc(CONDOMINIUM)).thenReturn(List.of());

        listener.onFeatureChanged(new FeatureChanged(CONDOMINIUM, FeatureService.ASSISTANT, true, "admin"));

        verify(events, never()).publishEvent(any(Object.class));
    }

    private static SourceFile file() {
        return new SourceFile(CONDOMINIUM, FileCategory.ATA, "ata.pdf", "c/ATA/2026/" + UUID.randomUUID() + "-ata.pdf",
                UUID.randomUUID().toString().replace("-", "").repeat(2), 10, "application/pdf", "gestor");
    }
}
