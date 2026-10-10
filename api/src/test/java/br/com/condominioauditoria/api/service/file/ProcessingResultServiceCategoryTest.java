package br.com.condominioauditoria.api.service.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.messaging.ProcessingResultMessage;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.CashFlow;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.LedgerEntryData;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.Section;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.Status;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.FileStatus;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.accounting.FundBalanceRepository;
import br.com.condominioauditoria.api.repository.accounting.FundRepository;
import br.com.condominioauditoria.api.repository.accounting.LedgerEntryRepository;
import br.com.condominioauditoria.api.repository.accounting.TotalsCheckRepository;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.service.budget.BudgetImportService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * RF-01.7: only the trial balance and cash flow category creates entries; in the others, the old extraction is deleted.
 */
class ProcessingResultServiceCategoryTest {

    private final SourceFileRepository files = mock(SourceFileRepository.class);
    private final LedgerEntryRepository entries = mock(LedgerEntryRepository.class);
    private final FundBalanceRepository balances = mock(FundBalanceRepository.class);
    private final TotalsCheckRepository totalsChecks = mock(TotalsCheckRepository.class);
    private final ProcessingResultService service = new ProcessingResultService(files, mock(FundRepository.class),
            entries, balances, totalsChecks, mock(BudgetImportService.class), event -> { });

    @Test
    void cashFlowInAnotherCategoryDeletesExtractionAndSavesNoEntries() {
        SourceFile file = new SourceFile(UUID.randomUUID(), FileCategory.CONTRACT, "fluxo.pdf", "c/fluxo.pdf",
                "b".repeat(64), 100, "application/pdf", "gestor");
        var entry = new LedgerEntryData(1, 1, LocalDate.of(2026, 9, 5), "1621", "Material hidráulico", null,
                "Compra de registro", new BigDecimal("0.00"), new BigDecimal("150.00"), new BigDecimal("850.00"), null);
        var cashFlow = new CashFlow("Mio", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                List.of(new Section("Ordinário", new BigDecimal("1000.00"), List.of(entry),
                        new BigDecimal("0.00"), new BigDecimal("150.00"))),
                List.of(), null);
        var result = new ProcessingResultMessage(3, file.getProcessingId(), file.getId(),
                file.getCondominiumId(), Status.COMPLETED, null, "fluxo-caixa-fundos", 1, cashFlow, null, List.of());
        when(files.findById(file.getId())).thenReturn(Optional.of(file));

        service.save(result);

        verify(entries).deleteByFileId(file.getId());
        verify(balances).deleteByFileId(file.getId());
        verify(totalsChecks).deleteByFileId(file.getId());
        verify(entries, never()).saveAll(any());
        assertThat(file.getStatus()).isEqualTo(FileStatus.COMPLETED);
        assertThat(file.getEntryCount()).isNull();
        assertThat(file.getMessage()).contains("Balancetes e fluxos de caixa");
    }
}
