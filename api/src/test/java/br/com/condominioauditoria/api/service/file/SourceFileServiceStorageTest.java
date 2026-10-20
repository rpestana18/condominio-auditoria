package br.com.condominioauditoria.api.service.file;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.exception.StorageWriteDeniedException;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.accounting.FundBalanceRepository;
import br.com.condominioauditoria.api.repository.accounting.FundRepository;
import br.com.condominioauditoria.api.repository.accounting.TotalsCheckRepository;
import br.com.condominioauditoria.api.repository.file.CategoryChangeRepository;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import br.com.condominioauditoria.storage.Storage;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

/** A data folder that refuses writes becomes a clear message, and nothing is recorded nor sent to the queue. */
class SourceFileServiceStorageTest {

    private final SourceFileRepository files = mock(SourceFileRepository.class);
    private final Storage storage = mock(Storage.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final SourceFileService service = new SourceFileService(files, storage, events,
            mock(CategoryChangeRepository.class), mock(FeatureService.class), mock(TotalsCheckRepository.class),
            mock(FundBalanceRepository.class), mock(FundRepository.class));

    @Test
    void writeDeniedBecomesClearMessageAndNothingIsSaved() throws Exception {
        when(files.findByCondominiumIdAndSha256(any(), any())).thenReturn(Optional.empty());
        doThrow(new AccessDeniedException("/dados/x")).when(storage).store(anyString(), any(InputStream.class));
        var upload = new MockMultipartFile("file", "ata.pdf", "application/pdf", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.upload(UUID.randomUUID(), FileCategory.MINUTES, upload, "gestor"))
                .isInstanceOf(StorageWriteDeniedException.class)
                .hasMessageContaining("pasta de dados do servidor não aceita gravação")
                .hasCauseInstanceOf(AccessDeniedException.class);
        verify(files, never()).save(any(SourceFile.class));
        verifyNoInteractions(events);
    }
}
