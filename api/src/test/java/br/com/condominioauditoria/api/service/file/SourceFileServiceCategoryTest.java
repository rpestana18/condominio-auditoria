package br.com.condominioauditoria.api.service.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.dto.response.file.SourceFileResponse;
import br.com.condominioauditoria.api.event.FileReadRequested;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.FileStatus;
import br.com.condominioauditoria.api.model.file.CategoryChange;
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
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** RF-01.7: changing the category records the change and reprocesses; the same category does nothing. */
class SourceFileServiceCategoryTest {

    private final SourceFileRepository files = mock(SourceFileRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CategoryChangeRepository categoryChanges = mock(CategoryChangeRepository.class);
    private final FeatureService features = mock(FeatureService.class);
    private final SourceFileService service = new SourceFileService(files, mock(Storage.class), events, categoryChanges,
            features, mock(TotalsCheckRepository.class), mock(FundBalanceRepository.class), mock(FundRepository.class));

    private SourceFile file;

    @BeforeEach
    void setUp() {
        file = new SourceFile(UUID.randomUUID(), FileCategory.CONTRACT, "fluxo-setembro.pdf",
                "c/CONTRACT/2026/abc-fluxo-setembro.pdf", "a".repeat(64), 100, "application/pdf", "gestor");
        file.complete(FileStatus.COMPLETED, "ok", null, null, null, null);
        when(files.save(any(SourceFile.class))).thenAnswer(i -> i.getArgument(0));
        when(files.findByIdAndCondominiumId(file.getId(), file.getCondominiumId())).thenReturn(Optional.of(file));
    }

    private SourceFileResponse changeCategory(FileCategory category, String username) {
        return service.changeCategory(file.getCondominiumId(), file.getId(), category, username);
    }

    @Test
    void changeRecordsHistoryAndReprocesses() {
        UUID processingIdBefore = file.getProcessingId();
        String pathBefore = file.getPath();

        SourceFileResponse saved = changeCategory(FileCategory.TRIAL_BALANCE, "gestor@condominio");

        assertThat(saved.category()).isEqualTo(FileCategory.TRIAL_BALANCE);
        assertThat(saved.status()).isEqualTo(FileStatus.PENDING);
        assertThat(file.getProcessingId()).isNotEqualTo(processingIdBefore);
        assertThat(file.getPath()).isEqualTo(pathBefore);
        verify(events).publishEvent(new FileReadRequested(file.getId()));

        var registry = ArgumentCaptor.forClass(CategoryChange.class);
        verify(categoryChanges).save(registry.capture());
        assertThat(registry.getValue().getPreviousCategory()).isEqualTo(FileCategory.CONTRACT);
        assertThat(registry.getValue().getNewCategory()).isEqualTo(FileCategory.TRIAL_BALANCE);
        assertThat(registry.getValue().getChangedBy()).isEqualTo("gestor@condominio");
        assertThat(registry.getValue().getChangedAt()).isNotNull();
    }

    @Test
    void sameCategoryDoesNotReprocess() {
        UUID processingIdBefore = file.getProcessingId();

        changeCategory(FileCategory.CONTRACT, "gestor");

        assertThat(file.getProcessingId()).isEqualTo(processingIdBefore);
        verify(categoryChanges, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void fileBeingProcessedRejectsChange() {
        file.startProcessing();

        assertThatThrownBy(() -> changeCategory(FileCategory.TRIAL_BALANCE, "gestor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("O arquivo já está sendo processado");
        assertThat(file.getCategory()).isEqualTo(FileCategory.CONTRACT);
        verify(categoryChanges, never()).save(any());
    }
}
