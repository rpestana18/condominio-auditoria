package br.com.condominioauditoria.api.service.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.messaging.ProcessingResultMessage;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.Status;
import br.com.condominioauditoria.api.model.accounting.TotalsCheck;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.FileStatus;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.orcamento.EstadoPrevisao;
import br.com.condominioauditoria.api.orcamento.GravacaoPrevisao;
import br.com.condominioauditoria.api.orcamento.LinhaPo;
import br.com.condominioauditoria.api.orcamento.LinhaPoRepository;
import br.com.condominioauditoria.api.orcamento.PoDoPiloto;
import br.com.condominioauditoria.api.orcamento.PrevisaoOrcamentaria;
import br.com.condominioauditoria.api.orcamento.PrevisaoOrcamentariaRepository;
import br.com.condominioauditoria.api.orcamento.PropriedadesOrcamento;
import br.com.condominioauditoria.api.repository.accounting.FundBalanceRepository;
import br.com.condominioauditoria.api.repository.accounting.FundRepository;
import br.com.condominioauditoria.api.repository.accounting.LedgerEntryRepository;
import br.com.condominioauditoria.api.repository.accounting.TotalsCheckRepository;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** ADR 0004, step 4: saving the budget read, only for a file in the PO category (RF-03.1.1 and RF-03.1.2). */
class ProcessingResultServiceBudgetTest {

    private final SourceFileRepository files = mock(SourceFileRepository.class);
    private final LedgerEntryRepository entries = mock(LedgerEntryRepository.class);
    private final FundBalanceRepository balances = mock(FundBalanceRepository.class);
    private final TotalsCheckRepository totalsChecks = mock(TotalsCheckRepository.class);
    private final PrevisaoOrcamentariaRepository budgets = mock(PrevisaoOrcamentariaRepository.class);
    private final LinhaPoRepository lines = mock(LinhaPoRepository.class);
    private final ProcessingResultService service = new ProcessingResultService(files, mock(FundRepository.class),
            entries, balances, totalsChecks,
            new GravacaoPrevisao(budgets, lines, new PropriedadesOrcamento(new BigDecimal("0.01"))), event -> { });

    private final List<PrevisaoOrcamentaria> saved = new ArrayList<>();
    private SourceFile file;

    @BeforeEach
    void setUp() {
        file = newFile(FileCategory.PO);
        when(budgets.findByArquivoId(any())).thenReturn(Optional.empty());
        when(budgets.save(any())).thenAnswer(i -> {
            saved.add(i.getArgument(0));
            return i.getArgument(0);
        });
    }

    @Test
    void budgetSavedWithFilePageAndHash() {
        service.save(result(PoDoPiloto.padrao()));

        assertThat(saved).hasSize(1);
        PrevisaoOrcamentaria budget = saved.getFirst();
        assertThat(budget.getArquivoId()).isEqualTo(file.getId());
        assertThat(budget.getCondominioId()).isEqualTo(file.getCondominiumId());
        assertThat(budget.getSha256()).isEqualTo(file.getSha256());
        assertThat(budget.getEstado()).isEqualTo(EstadoPrevisao.LIDA);
        assertThat(budget.getInterpretador()).isEqualTo("po-protest");
        assertThat(budget.getTitulo()).isEqualTo("PROPOSTA ORÇAMENTÁRIA 2026 / 2027");
        assertThat(budget.getColunaOrcadoAnterior()).isEqualTo("2025/2026");
        assertThat(budget.getColunaOrcado()).isEqualTo("2026/2027");
        assertThat(budget.getTotalImpresso()).isEqualByComparingTo("474201.13");
        assertThat(budget.getPrevistoMesImpresso()).isEqualByComparingTo("451620.12");
        assertThat(budget.getPrevistoMes()).isEqualByComparingTo("451620.13");
        assertThat(budget.getToleranciaArredondamento()).isEqualByComparingTo("0.01");

        List<LinhaPo> saved = savedLines();
        assertThat(saved).hasSize(PoDoPiloto.padrao().previsao().lines().size());
        assertThat(saved).allSatisfy(l -> {
            assertThat(l.getArquivoId()).isEqualTo(file.getId());
            assertThat(l.getSha256()).isEqualTo(file.getSha256());
            assertThat(l.getPagina()).isEqualTo(1);
            assertThat(l.getPrevisaoId()).isEqualTo(budget.getId());
            assertThat(l.getCodigoEfetivo()).isEqualTo(l.getCodigoImpresso());
        });
        LinhaPo management = saved.stream().filter(l -> l.getCodigoImpresso().equals("1.3.20")).findFirst()
                .orElseThrow();
        assertThat(management.getConta()).isEqualTo("1682 - Sindicatura Profissional");
        assertThat(management.getDescricao()).isEqualTo("Obm - Sergio Diniz");
        assertThat(management.getOrcado()).isEqualByComparingTo("8000.00");
        assertThat(management.getPercentualTexto()).isEqualTo("-53,47%");
        assertThat(saved).filteredOn(l -> l.getCodigoImpresso().equals("1.3.2")).hasSize(2);

        verify(totalsChecks, times(PoDoPiloto.padrao().conferencias().size())).save(any(TotalsCheck.class));
        verify(entries, never()).saveAll(any());
        assertThat(file.getStatus()).isEqualTo(FileStatus.CONCLUIDO);
        assertThat(file.getMessage()).isEqualTo("PO lida: aguarda a confirmação do Admin");
    }

    @Test
    void failedCheckLeavesTheBudgetReadWithDivergence() {
        service.save(result(PoDoPiloto.padrao().comSubtotalPessoal("69193.00")));

        assertThat(saved.getFirst().getEstado()).isEqualTo(EstadoPrevisao.LIDA_COM_DIVERGENCIA);
        assertThat(file.getStatus()).isEqualTo(FileStatus.PRECISA_REVISAO);
        assertThat(file.getMessage()).contains("divergência");
    }

    @Test
    void budgetInAnotherCategoryIsNotSaved() {
        file = newFile(FileCategory.OUTROS);

        service.save(result(PoDoPiloto.padrao()));

        verify(budgets, never()).save(any());
        verify(lines, never()).saveAll(any());
        verify(totalsChecks, never()).save(any());
        assertThat(file.getStatus()).isEqualTo(FileStatus.CONCLUIDO);
        assertThat(file.getMessage()).contains("Previsão orçamentária");
    }

    @Test
    void movingTheBudgetToAnotherCategoryRemovesTheUnconfirmedBudget() {
        PrevisaoOrcamentaria previous = new PrevisaoOrcamentaria(file.getCondominiumId(), file.getId(),
                file.getSha256());
        previous.registrarLeitura("po-protest", "t", "e", "a", "b", EstadoPrevisao.LIDA, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, new BigDecimal("0.01"), java.time.Instant.now());
        file = newFile(FileCategory.CONTRATO);
        when(budgets.findByArquivoId(file.getId())).thenReturn(Optional.of(previous));

        service.save(result(PoDoPiloto.padrao()));

        verify(lines).apagarDaPrevisao(previous.getId());
        verify(budgets).delete(previous);
        verify(budgets, never()).save(any());
    }

    @Test
    void reprocessingTheSameFileDoesNotDuplicateTheBudget() {
        service.save(result(PoDoPiloto.padrao()));
        PrevisaoOrcamentaria first = saved.getFirst();
        when(budgets.findByArquivoId(file.getId())).thenReturn(Optional.of(first));
        file.requestProcessing();

        service.save(result(PoDoPiloto.padrao()));

        assertThat(saved).hasSize(2);
        assertThat(saved.get(1)).isSameAs(first);
        // The lines of the previous read go out before the new ones come in
        verify(lines, times(2)).apagarDaPrevisao(first.getId());
        verify(totalsChecks, times(2)).deleteByFileId(file.getId());
    }

    @Test
    void confirmedBudgetDoesNotChangeOnReprocess() {
        PrevisaoOrcamentaria confirmed = new PrevisaoOrcamentaria(file.getCondominiumId(), file.getId(),
                file.getSha256());
        confirmed.registrarLeitura("po-protest", "t", "e", "a", "b", EstadoPrevisao.LIDA, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("0.01"), java.time.Instant.now());
        confirmed.confirmar(1, YearMonth.of(2026, 5), YearMonth.of(2027, 4), null, true, LocalDate.of(2026, 5, 20),
                false, null, "admin", java.time.Instant.now());
        when(budgets.findByArquivoId(file.getId())).thenReturn(Optional.of(confirmed));

        service.save(result(PoDoPiloto.padrao().comSubtotalPessoal("69193.00")));

        verify(totalsChecks, never()).deleteByFileId(any());
        verify(lines, never()).apagarDaPrevisao(any());
        verify(budgets, never()).save(any());
        assertThat(confirmed.getEstado()).isEqualTo(EstadoPrevisao.CONFIRMADA);
        assertThat(file.getMessage()).contains("já foi confirmada");
    }

    @SuppressWarnings("unchecked")
    private List<LinhaPo> savedLines() {
        ArgumentCaptor<List<LinhaPo>> captor = ArgumentCaptor.forClass(List.class);
        verify(lines).saveAll(captor.capture());
        return captor.getValue();
    }

    private ProcessingResultMessage result(PoDoPiloto budget) {
        return new ProcessingResultMessage(2, file.getProcessingId(), file.getId(), file.getCondominiumId(),
                Status.CONCLUIDO, null, "po-protest", 1, null, budget.previsao(), budget.conferencias());
    }

    private SourceFile newFile(FileCategory category) {
        SourceFile a = new SourceFile(UUID.randomUUID(), category, "PO-2026-2027-aprovada.pdf", "c/PO/po.pdf",
                "f".repeat(64), 100, "application/pdf", "admin");
        when(files.findById(a.getId())).thenReturn(Optional.of(a));
        return a;
    }
}
