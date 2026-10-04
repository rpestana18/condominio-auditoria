package br.com.condominioauditoria.backend.processamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import br.com.condominioauditoria.backend.arquivo.ArquivoRepository;
import br.com.condominioauditoria.backend.arquivo.Categoria;
import br.com.condominioauditoria.backend.arquivo.StatusArquivo;
import br.com.condominioauditoria.backend.contabil.ConferenciaRepository;
import br.com.condominioauditoria.backend.contabil.FundoRepository;
import br.com.condominioauditoria.backend.contabil.LancamentoRepository;
import br.com.condominioauditoria.backend.contabil.SaldoFundoRepository;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.Fluxo;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.LancamentoLido;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.Secao;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.Situacao;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-01.7: só a categoria de balancetes e fluxos de caixa gera lançamentos; nas outras, a extração antiga é apagada. */
class GravacaoResultadoCategoriaTest {

    private final ArquivoRepository arquivos = mock(ArquivoRepository.class);
    private final LancamentoRepository lancamentos = mock(LancamentoRepository.class);
    private final SaldoFundoRepository saldos = mock(SaldoFundoRepository.class);
    private final ConferenciaRepository conferencias = mock(ConferenciaRepository.class);
    private final GravacaoResultado gravacao = new GravacaoResultado(arquivos, mock(FundoRepository.class),
            lancamentos, saldos, conferencias);

    @Test
    void fluxoEmOutraCategoriaApagaExtracaoENaoGravaLancamentos() {
        Arquivo arquivo = new Arquivo(UUID.randomUUID(), Categoria.CONTRATO, "fluxo.pdf", "c/fluxo.pdf",
                "b".repeat(64), 100, "application/pdf", "gestor");
        var lancamento = new LancamentoLido(1, 1, LocalDate.of(2026, 9, 5), "1621", "Material hidráulico", null,
                "Compra de registro", new BigDecimal("0.00"), new BigDecimal("150.00"), new BigDecimal("850.00"), null);
        var fluxo = new Fluxo("Mio", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                List.of(new Secao("Ordinário", new BigDecimal("1000.00"), List.of(lancamento),
                        new BigDecimal("0.00"), new BigDecimal("150.00"))),
                List.of(), null);
        var resultado = new ResultadoProcessamento(2, arquivo.getProcessamentoId(), arquivo.getId(),
                arquivo.getCondominioId(), Situacao.CONCLUIDO, null, "fluxo-caixa-fundos", 1, fluxo, null, List.of());
        when(arquivos.findById(arquivo.getId())).thenReturn(Optional.of(arquivo));

        gravacao.gravar(resultado);

        verify(lancamentos).apagarDoArquivo(arquivo.getId());
        verify(saldos).apagarDoArquivo(arquivo.getId());
        verify(conferencias).apagarDoArquivo(arquivo.getId());
        verify(lancamentos, never()).saveAll(any());
        assertThat(arquivo.getStatus()).isEqualTo(StatusArquivo.CONCLUIDO);
        assertThat(arquivo.getTotalLancamentos()).isNull();
        assertThat(arquivo.getMensagem()).contains("Balancetes e fluxos de caixa");
    }

    /** ADR 0004, passo 1: a PO lida (v2) é aceita e o arquivo conclui, mas a PO ainda não é gravada (passo 4). */
    @Test
    void poLidaEhAceitaSemGravarNada() {
        Arquivo arquivo = new Arquivo(UUID.randomUUID(), Categoria.PO, "po.pdf", "c/po.pdf",
                "d".repeat(64), 100, "application/pdf", "admin");
        var linha = new ResultadoProcessamento.LinhaPoLida(1, 1, ResultadoProcessamento.TipoLinhaPo.TOTAL, "1", null,
                "Soma das seções 1.1 a 1.9", null, "TOTAL DAS DESPESAS", new BigDecimal("441304.38"), new BigDecimal("474201.13"), "7,45%", null);
        var po = new ResultadoProcessamento.PrevisaoLida("PROPOSTA ORÇAMENTÁRIA 2026 / 2027", "2026 / 2027",
                List.of("2025/2026", "2026/2027"), List.of(linha));
        var resultado = new ResultadoProcessamento(2, arquivo.getProcessamentoId(), arquivo.getId(),
                arquivo.getCondominioId(), Situacao.CONCLUIDO, null, "po-protest", 1, null, po, List.of());
        when(arquivos.findById(arquivo.getId())).thenReturn(Optional.of(arquivo));

        gravacao.gravar(resultado);

        verify(lancamentos, never()).saveAll(any());
        verify(saldos, never()).saveAll(any());
        verify(conferencias, never()).save(any());
        assertThat(arquivo.getStatus()).isEqualTo(StatusArquivo.CONCLUIDO);
        assertThat(arquivo.getTotalLancamentos()).isNull();
    }
}
