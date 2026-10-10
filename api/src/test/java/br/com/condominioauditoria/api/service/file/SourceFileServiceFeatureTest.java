package br.com.condominioauditoria.api.service.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.dto.response.file.SourceFileResponse;
import br.com.condominioauditoria.api.event.FileIndexRequested;
import br.com.condominioauditoria.api.event.FileReadRequested;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.FileStatus;
import br.com.condominioauditoria.api.model.enums.IndexingStatus;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.accounting.FundBalanceRepository;
import br.com.condominioauditoria.api.repository.accounting.FundRepository;
import br.com.condominioauditoria.api.repository.accounting.TotalsCheckRepository;
import br.com.condominioauditoria.api.repository.file.CategoryChangeRepository;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import br.com.condominioauditoria.storage.Storage;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

/**
 * RF-10.3: with the Assistant disabled the file only goes through the core (reading) and no indexing request is made;
 * the indexing status stays null. With it enabled, upload and reprocess request indexing.
 */
class SourceFileServiceFeatureTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();

    private final SourceFileRepository files = mock(SourceFileRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final FeatureService features = mock(FeatureService.class);
    private final SourceFileService service = new SourceFileService(files, mock(Storage.class), events,
            mock(CategoryChangeRepository.class), features, mock(TotalsCheckRepository.class),
            mock(FundBalanceRepository.class), mock(FundRepository.class));

    @BeforeEach
    void setUp() {
        when(files.save(any(SourceFile.class))).thenAnswer(i -> i.getArgument(0));
        when(files.findByCondominiumIdAndSha256(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void uploadWithFeatureDisabledOnlyReads() throws Exception {
        when(features.isEnabled(CONDOMINIUM, FeatureService.ASSISTANT)).thenReturn(false);

        SourceFileResponse file = service.upload(CONDOMINIUM, FileCategory.MINUTES, upload(), "gestor");

        assertThat(file.indexing()).isNull();
        verify(events).publishEvent(new FileReadRequested(file.id()));
        verify(events, never()).publishEvent(any(FileIndexRequested.class));
    }

    @Test
    void uploadWithFeatureEnabledRequestsIndexing() throws Exception {
        when(features.isEnabled(CONDOMINIUM, FeatureService.ASSISTANT)).thenReturn(true);

        SourceFileResponse file = service.upload(CONDOMINIUM, FileCategory.MINUTES, upload(), "gestor");

        assertThat(file.indexing().status()).isEqualTo(IndexingStatus.QUEUED);
        verify(events).publishEvent(new FileIndexRequested(file.id()));
    }

    @Test
    void reprocessWithFeatureDisabledKeepsIndexingStatus() {
        when(features.isEnabled(CONDOMINIUM, FeatureService.ASSISTANT)).thenReturn(false);
        SourceFile file = new SourceFile(CONDOMINIUM, FileCategory.MINUTES, "ata.pdf", "c/MINUTES/2026/x-ata.pdf", "a".repeat(64), 10,
                "application/pdf", "gestor");
        file.requestIndexing();
        file.completeIndexing(IndexingStatus.INDEXED, null, 2, 3);
        UUID request = file.getIndexingId();

        when(files.findByIdAndCondominiumId(file.getId(), CONDOMINIUM)).thenReturn(Optional.of(file));

        service.reprocess(CONDOMINIUM, file.getId());

        assertThat(file.getStatus()).isEqualTo(FileStatus.PENDING);
        assertThat(file.getIndexingStatus()).isEqualTo(IndexingStatus.INDEXED); // index kept (Q14)
        assertThat(file.getIndexingId()).isEqualTo(request);
        verify(events).publishEvent(new FileReadRequested(file.getId()));
        verify(events, never()).publishEvent(any(FileIndexRequested.class));
    }

    private static MockMultipartFile upload() {
        return new MockMultipartFile("arquivo", "ata.pdf", "application/pdf",
                ("conteudo " + UUID.randomUUID()).getBytes());
    }
}
