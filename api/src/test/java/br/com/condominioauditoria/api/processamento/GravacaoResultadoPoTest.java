package br.com.condominioauditoria.api.processamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.arquivo.Arquivo;
import br.com.condominioauditoria.api.arquivo.ArquivoRepository;
import br.com.condominioauditoria.api.arquivo.Categoria;
import br.com.condominioauditoria.api.arquivo.StatusArquivo;
import br.com.condominioauditoria.api.contabil.Conferencia;
import br.com.condominioauditoria.api.contabil.ConferenciaRepository;
import br.com.condominioauditoria.api.contabil.FundoRepository;
import br.com.condominioauditoria.api.contabil.LancamentoRepository;
import br.com.condominioauditoria.api.contabil.SaldoFundoRepository;
import br.com.condominioauditoria.api.mensagens.ResultadoProcessamento;
import br.com.condominioauditoria.api.mensagens.ResultadoProcessamento.Situacao;
import br.com.condominioauditoria.api.orcamento.EstadoPrevisao;
import br.com.condominioauditoria.api.orcamento.GravacaoPrevisao;
import br.com.condominioauditoria.api.orcamento.LinhaPo;
import br.com.condominioauditoria.api.orcamento.LinhaPoRepository;
import br.com.condominioauditoria.api.orcamento.PoDoPiloto;
import br.com.condominioauditoria.api.orcamento.PrevisaoOrcamentaria;
import br.com.condominioauditoria.api.orcamento.PrevisaoOrcamentariaRepository;
import br.com.condominioauditoria.api.orcamento.PropriedadesOrcamento;
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

/** ADR 0004, passo 4: gravação da PO lida, só para arquivo da categoria PO (RF-03.1.1 e RF-03.1.2). */
class GravacaoResultadoPoTest {

    private final ArquivoRepository arquivos = mock(ArquivoRepository.class);
    private final LancamentoRepository lancamentos = mock(LancamentoRepository.class);
    private final SaldoFundoRepository saldos = mock(SaldoFundoRepository.class);
    private final ConferenciaRepository conferencias = mock(ConferenciaRepository.class);
    private final PrevisaoOrcamentariaRepository previsoes = mock(PrevisaoOrcamentariaRepository.class);
    private final LinhaPoRepository linhas = mock(LinhaPoRepository.class);
    private final GravacaoResultado gravacao = new GravacaoResultado(arquivos, mock(FundoRepository.class),
            lancamentos, saldos, conferencias,
            new GravacaoPrevisao(previsoes, linhas, new PropriedadesOrcamento(new BigDecimal("0.01"))), evento -> { });

    private final List<PrevisaoOrcamentaria> salvas = new ArrayList<>();
    private Arquivo arquivo;

    @BeforeEach
    void preparar() {
        arquivo = novoArquivo(Categoria.PO);
        when(previsoes.findByArquivoId(any())).thenReturn(Optional.empty());
        when(previsoes.save(any())).thenAnswer(i -> {
            salvas.add(i.getArgument(0));
            return i.getArgument(0);
        });
    }

    @Test
    void poGravadaComArquivoPaginaEHash() {
        gravacao.gravar(resultado(PoDoPiloto.padrao()));

        assertThat(salvas).hasSize(1);
        PrevisaoOrcamentaria po = salvas.getFirst();
        assertThat(po.getArquivoId()).isEqualTo(arquivo.getId());
        assertThat(po.getCondominioId()).isEqualTo(arquivo.getCondominioId());
        assertThat(po.getSha256()).isEqualTo(arquivo.getSha256());
        assertThat(po.getEstado()).isEqualTo(EstadoPrevisao.LIDA);
        assertThat(po.getInterpretador()).isEqualTo("po-protest");
        assertThat(po.getTitulo()).isEqualTo("PROPOSTA ORÇAMENTÁRIA 2026 / 2027");
        assertThat(po.getColunaOrcadoAnterior()).isEqualTo("2025/2026");
        assertThat(po.getColunaOrcado()).isEqualTo("2026/2027");
        assertThat(po.getTotalImpresso()).isEqualByComparingTo("474201.13");
        assertThat(po.getPrevistoMesImpresso()).isEqualByComparingTo("451620.12");
        assertThat(po.getPrevistoMes()).isEqualByComparingTo("451620.13");
        assertThat(po.getToleranciaArredondamento()).isEqualByComparingTo("0.01");

        List<LinhaPo> gravadas = linhasSalvas();
        assertThat(gravadas).hasSize(PoDoPiloto.padrao().previsao().linhas().size());
        assertThat(gravadas).allSatisfy(l -> {
            assertThat(l.getArquivoId()).isEqualTo(arquivo.getId());
            assertThat(l.getSha256()).isEqualTo(arquivo.getSha256());
            assertThat(l.getPagina()).isEqualTo(1);
            assertThat(l.getPrevisaoId()).isEqualTo(po.getId());
            assertThat(l.getCodigoEfetivo()).isEqualTo(l.getCodigoImpresso());
        });
        LinhaPo sindicatura = gravadas.stream().filter(l -> l.getCodigoImpresso().equals("1.3.20")).findFirst()
                .orElseThrow();
        assertThat(sindicatura.getConta()).isEqualTo("1682 - Sindicatura Profissional");
        assertThat(sindicatura.getDescricao()).isEqualTo("Obm - Sergio Diniz");
        assertThat(sindicatura.getOrcado()).isEqualByComparingTo("8000.00");
        assertThat(sindicatura.getPercentualTexto()).isEqualTo("-53,47%");
        assertThat(gravadas).filteredOn(l -> l.getCodigoImpresso().equals("1.3.2")).hasSize(2);

        verify(conferencias, times(PoDoPiloto.padrao().conferencias().size())).save(any(Conferencia.class));
        verify(lancamentos, never()).saveAll(any());
        assertThat(arquivo.getStatus()).isEqualTo(StatusArquivo.CONCLUIDO);
        assertThat(arquivo.getMensagem()).isEqualTo("PO lida: aguarda a confirmação do Admin");
    }

    @Test
    void conferenciaFalhaDeixaAPoLidaComDivergencia() {
        gravacao.gravar(resultado(PoDoPiloto.padrao().comSubtotalPessoal("69193.00")));

        assertThat(salvas.getFirst().getEstado()).isEqualTo(EstadoPrevisao.LIDA_COM_DIVERGENCIA);
        assertThat(arquivo.getStatus()).isEqualTo(StatusArquivo.PRECISA_REVISAO);
        assertThat(arquivo.getMensagem()).contains("divergência");
    }

    @Test
    void poEmOutraCategoriaNaoEhGravada() {
        arquivo = novoArquivo(Categoria.OUTROS);

        gravacao.gravar(resultado(PoDoPiloto.padrao()));

        verify(previsoes, never()).save(any());
        verify(linhas, never()).saveAll(any());
        verify(conferencias, never()).save(any());
        assertThat(arquivo.getStatus()).isEqualTo(StatusArquivo.CONCLUIDO);
        assertThat(arquivo.getMensagem()).contains("Previsão orçamentária");
    }

    @Test
    void trocarAPoParaOutraCategoriaRemoveAPoNaoConfirmada() {
        PrevisaoOrcamentaria anterior = new PrevisaoOrcamentaria(arquivo.getCondominioId(), arquivo.getId(),
                arquivo.getSha256());
        anterior.registrarLeitura("po-protest", "t", "e", "a", "b", EstadoPrevisao.LIDA, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, new BigDecimal("0.01"), java.time.Instant.now());
        arquivo = novoArquivo(Categoria.CONTRATO);
        when(previsoes.findByArquivoId(arquivo.getId())).thenReturn(Optional.of(anterior));

        gravacao.gravar(resultado(PoDoPiloto.padrao()));

        verify(linhas).apagarDaPrevisao(anterior.getId());
        verify(previsoes).delete(anterior);
        verify(previsoes, never()).save(any());
    }

    @Test
    void reprocessarOMesmoArquivoNaoDuplicaAPo() {
        gravacao.gravar(resultado(PoDoPiloto.padrao()));
        PrevisaoOrcamentaria primeira = salvas.getFirst();
        when(previsoes.findByArquivoId(arquivo.getId())).thenReturn(Optional.of(primeira));
        arquivo.novoProcessamento();

        gravacao.gravar(resultado(PoDoPiloto.padrao()));

        assertThat(salvas).hasSize(2);
        assertThat(salvas.get(1)).isSameAs(primeira);
        // As linhas da leitura anterior saem antes de entrarem as novas
        verify(linhas, times(2)).apagarDaPrevisao(primeira.getId());
        verify(conferencias, times(2)).apagarDoArquivo(arquivo.getId());
    }

    @Test
    void poConfirmadaNaoMudaComReprocesso() {
        PrevisaoOrcamentaria confirmada = new PrevisaoOrcamentaria(arquivo.getCondominioId(), arquivo.getId(),
                arquivo.getSha256());
        confirmada.registrarLeitura("po-protest", "t", "e", "a", "b", EstadoPrevisao.LIDA, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("0.01"), java.time.Instant.now());
        confirmada.confirmar(1, YearMonth.of(2026, 5), YearMonth.of(2027, 4), null, true, LocalDate.of(2026, 5, 20),
                false, null, "admin", java.time.Instant.now());
        when(previsoes.findByArquivoId(arquivo.getId())).thenReturn(Optional.of(confirmada));

        gravacao.gravar(resultado(PoDoPiloto.padrao().comSubtotalPessoal("69193.00")));

        verify(conferencias, never()).apagarDoArquivo(any());
        verify(linhas, never()).apagarDaPrevisao(any());
        verify(previsoes, never()).save(any());
        assertThat(confirmada.getEstado()).isEqualTo(EstadoPrevisao.CONFIRMADA);
        assertThat(arquivo.getMensagem()).contains("já foi confirmada");
    }

    @SuppressWarnings("unchecked")
    private List<LinhaPo> linhasSalvas() {
        ArgumentCaptor<List<LinhaPo>> captor = ArgumentCaptor.forClass(List.class);
        verify(linhas).saveAll(captor.capture());
        return captor.getValue();
    }

    private ResultadoProcessamento resultado(PoDoPiloto po) {
        return new ResultadoProcessamento(2, arquivo.getProcessamentoId(), arquivo.getId(), arquivo.getCondominioId(),
                Situacao.CONCLUIDO, null, "po-protest", 1, null, po.previsao(), po.conferencias());
    }

    private Arquivo novoArquivo(Categoria categoria) {
        Arquivo a = new Arquivo(UUID.randomUUID(), categoria, "PO-2026-2027-aprovada.pdf", "c/PO/po.pdf",
                "f".repeat(64), 100, "application/pdf", "admin");
        when(arquivos.findById(a.getId())).thenReturn(Optional.of(a));
        return a;
    }
}
