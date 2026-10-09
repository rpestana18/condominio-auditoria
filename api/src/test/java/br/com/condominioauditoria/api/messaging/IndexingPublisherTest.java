package br.com.condominioauditoria.api.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.config.QueueConfig;
import br.com.condominioauditoria.api.config.properties.ApiProperties;
import br.com.condominioauditoria.api.event.FileIndexRequested;
import br.com.condominioauditoria.api.event.FilesIndexRequested;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.IndexingStatus;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.modulo.Modulos;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * An indexing request goes to rag.indexacao following the contract; the sweep resends what stalled and gives up after
 * 3.
 */
class IndexingPublisherTest {

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final SourceFileRepository files = mock(SourceFileRepository.class);
    private final Modulos features = mock(Modulos.class);
    private final IndexingPublisher publisher = new IndexingPublisher(rabbit, new MessageContract(), files,
            mock(PlatformTransactionManager.class),
            new ApiProperties(null, new ApiProperties.ProcessingProperties(15, 3), null, null), features,
            Runnable::run);

    @BeforeEach
    void featureEnabled() {
        when(features.ligado(any(UUID.class), eq(Modulos.ASSISTENTE))).thenReturn(true);
    }

    @Test
    void afterCommitPublishesOnTheIndexingQueue() {
        SourceFile file = file();
        when(files.findById(file.getId())).thenReturn(Optional.of(file));

        publisher.afterCommit(new FileIndexRequested(file.getId()));

        var message = ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq(""), eq(QueueConfig.INDEXING), message.capture());
        String json = new String(message.getValue().getBody(), StandardCharsets.UTF_8);
        assertThat(json).contains(file.getIndexingId().toString()).contains("\"operacao\":\"INDEXAR\"");
    }

    @Test
    void sweepResendsTheSameRequestAndGivesUpOnTheThirdAttempt() {
        SourceFile stalled = file();
        UUID request = stalled.getIndexingId();
        SourceFile exhausted = file();
        exhausted.resendIndexing();
        exhausted.resendIndexing(); // 3 attempts
        when(files.findByIndexingStatusInAndIndexingQueuedAtBeforeOrderByUploadedAt(anyCollection(), any()))
                .thenReturn(List.of(stalled, exhausted));

        publisher.sweep(Instant.now());

        assertThat(stalled.getIndexingAttempts()).isEqualTo(2);
        assertThat(stalled.getIndexingId()).isEqualTo(request);
        assertThat(stalled.getIndexingStatus()).isEqualTo(IndexingStatus.NA_FILA);
        assertThat(exhausted.getIndexingStatus()).isEqualTo(IndexingStatus.ERRO);
        assertThat(exhausted.getIndexingReason()).contains("3 tentativas");
        verify(rabbit, times(1)).send(eq(""), eq(QueueConfig.INDEXING), any(Message.class));
    }

    @Test
    void sweepWithoutStalledFilesPublishesNothing() {
        when(files.findByIndexingStatusInAndIndexingQueuedAtBeforeOrderByUploadedAt(anyCollection(), any()))
                .thenReturn(List.of());

        publisher.sweep(Instant.now());

        verify(rabbit, never()).send(any(String.class), any(String.class), any(Message.class));
    }

    @Test
    void disabledFeaturePublishesNeitherSingleNorBatch() {
        SourceFile file = file();
        when(features.ligado(file.getCondominiumId(), Modulos.ASSISTENTE)).thenReturn(false);
        when(files.findById(file.getId())).thenReturn(Optional.of(file));
        when(files.findAllById(List.of(file.getId()))).thenReturn(List.of(file));

        publisher.afterCommit(new FileIndexRequested(file.getId()));
        publisher.afterCommit(new FilesIndexRequested(file.getCondominiumId(), List.of(file.getId())));

        verify(rabbit, never()).send(any(String.class), any(String.class), any(Message.class));
    }

    @Test
    void sweepSkipsCondominiumWithDisabledFeatureWithoutSpendingAttempts() {
        SourceFile stalled = file();
        when(features.ligado(stalled.getCondominiumId(), Modulos.ASSISTENTE)).thenReturn(false);
        when(files.findByIndexingStatusInAndIndexingQueuedAtBeforeOrderByUploadedAt(anyCollection(), any()))
                .thenReturn(List.of(stalled));

        publisher.sweep(Instant.now());

        assertThat(stalled.getIndexingAttempts()).isEqualTo(1);
        assertThat(stalled.getIndexingStatus()).isEqualTo(IndexingStatus.NA_FILA);
        verify(rabbit, never()).send(any(String.class), any(String.class), any(Message.class));
    }

    @Test
    void batchPublishesOneRequestPerFile() {
        UUID condominium = UUID.randomUUID();
        List<SourceFile> batch = List.of(file(condominium), file(condominium), file(condominium));
        List<UUID> ids = batch.stream().map(SourceFile::getId).toList();
        when(files.findAllById(ids)).thenReturn(batch);

        publisher.afterCommit(new FilesIndexRequested(condominium, ids));

        var messages = ArgumentCaptor.forClass(Message.class);
        verify(rabbit, times(3)).send(eq(""), eq(QueueConfig.INDEXING), messages.capture());
        assertThat(messages.getAllValues()).extracting(m -> new String(m.getBody(), StandardCharsets.UTF_8))
                .allSatisfy(json -> assertThat(json).contains(condominium.toString()));
    }

    private static SourceFile file() {
        return file(UUID.randomUUID());
    }

    private static SourceFile file(UUID condominium) {
        SourceFile file = new SourceFile(condominium, FileCategory.BALANCETE, "fluxo.pdf", "c/BALANCETE/2026/x-fluxo.pdf",
                "b".repeat(64), 10, "application/pdf", "gestor");
        file.requestIndexing();
        return file;
    }
}
