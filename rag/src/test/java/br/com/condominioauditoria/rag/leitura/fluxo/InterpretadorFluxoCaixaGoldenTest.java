package br.com.condominioauditoria.rag.leitura.fluxo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.rag.dominio.fluxo.ConferenciaFluxo;
import br.com.condominioauditoria.rag.dominio.fluxo.FluxoDeCaixa;
import br.com.condominioauditoria.rag.dominio.fluxo.LancamentoFluxo;
import br.com.condominioauditoria.rag.dominio.fluxo.Verificacao;
import br.com.condominioauditoria.rag.leitura.contrato.ContratoLeitor;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Fluxo de caixa real de setembro/2026 do piloto. O arquivo é dado real e fica fora do git
 * (data/golden/privado); sem ele, o teste é pulado.
 */
class InterpretadorFluxoCaixaGoldenTest {

    private static FluxoDeCaixa fluxo;

    @BeforeAll
    static void ler() throws Exception {
        Path json = Path.of(System.getProperty("golden.dir"), "privado/fluxo-caixa-2026-09.documento-lido.json");
        assumeTrue(Files.exists(json), "golden privado ausente");
        var documento = new ContratoLeitor().converter(Files.readString(json));
        var interpretador = new InterpretadorFluxoCaixa();
        assertThat(interpretador.reconhece(documento)).isTrue();
        fluxo = interpretador.interpretar(documento);
    }

    @Test
    void cabecalho() {
        assertThat(fluxo.empreendimento()).isEqualTo("2815 - MIO RESIDENCIAL PARQUE");
        assertThat(fluxo.periodoInicio()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(fluxo.periodoFim()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void todosOsFundosELancamentos() {
        assertThat(fluxo.secoes()).hasSize(21);
        assertThat(fluxo.posicaoFinanceira()).hasSize(21);
        assertThat(fluxo.totalLancamentos()).isEqualTo(423);
        assertThat(fluxo.totalPosicao().saldoAtual()).isEqualByComparingTo("457051.86");
    }

    @Test
    void todasAsConferenciasPassam() {
        assertThat(ConferenciaFluxo.conferir(fluxo)).allSatisfy(v ->
                assertThat(v.ok()).as(v.codigo() + ": " + v.detalhe()).isTrue())
                .extracting(Verificacao::codigo)
                .containsExactly("SALDO_CORRENTE", "TOTAIS_FUNDO", "SALDO_FINAL_FUNDO", "TOTAL_POSICAO");
    }

    @Test
    void historicoNaoMisturaComOVizinho() {
        LancamentoFluxo primeiraCompra = fluxo.secoes().getFirst().lancamentos().get(1);
        assertThat(primeiraCompra.contaCodigo()).isEqualTo("1467");
        assertThat(primeiraCompra.contaNome()).isEqualTo("PEÇAS E ACESSÓRIOS");
        assertThat(primeiraCompra.documento()).isEqualTo("563946");
        assertThat(primeiraCompra.historico())
                .isEqualTo("COMPRA DE TOLDO, RECIBO: DE: MERCADO PAGO INSTITUICAO DE PAGAMENTO LTDA");
        assertThat(primeiraCompra.debito()).isEqualByComparingTo("1078.80");
        assertThat(primeiraCompra.enriquecimento().meioPagamento()).isEqualTo("MERCADO PAGO");
    }

    @Test
    void comprasPorMeioDePagamentoBatemComAAnaliseManual() {
        var compras = fluxo.secoes().stream().flatMap(s -> s.lancamentos().stream())
                .filter(l -> l.debito().signum() > 0)
                .filter(l -> Objects.equals(l.enriquecimento().meioPagamento(), "MERCADO PAGO"))
                .toList();
        assertThat(compras).hasSize(35);
        assertThat(compras.stream().map(LancamentoFluxo::debito).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("18215.37");
    }

    /** ADR 0004, Decisão 7 e RF-03.1.9: arrecadação dos fundos = créditos "RECIBOS ACUMULADOS". */
    @Test
    void recebimentosDeCotaDosFundosBatemComAAnaliseManual() {
        assertThat(somaRecebimentosDeCota("FUNDO DE RESERVA")).isEqualByComparingTo("14260.79");
        assertThat(somaRecebimentosDeCota("OBRAS / REFORMAS / INFRA")).isEqualByComparingTo("9705.06");
        assertThat(somaRecebimentosDeCota("OBRAS")).isEqualByComparingTo("25.13");
    }

    @Test
    void todosOsCreditosDoFundoDeReservaSaoRecebimentoDeCota() {
        assertThat(secao("FUNDO DE RESERVA").lancamentos()).filteredOn(l -> l.credito().signum() != 0)
                .hasSize(24)
                .allSatisfy(l -> assertThat(l.enriquecimento().recebimentoCota()).isTrue());
    }

    @Test
    void nenhumDebitoMarcadoComoRecebimentoDeCota() {
        var marcados = fluxo.secoes().stream().flatMap(s -> s.lancamentos().stream())
                .filter(l -> l.enriquecimento().recebimentoCota()).toList();
        assertThat(marcados).isNotEmpty().allSatisfy(l -> {
            assertThat(l.debito().signum()).isZero();
            assertThat(l.historico()).isEqualTo("RECIBOS ACUMULADOS");
        });
        assertThat(fluxo.secoes().stream().flatMap(s -> s.lancamentos().stream())
                .filter(l -> l.debito().signum() != 0 && l.enriquecimento().recebimentoCota())).isEmpty();
    }

    private static br.com.condominioauditoria.rag.dominio.fluxo.SecaoFundo secao(String fundo) {
        return fluxo.secoes().stream().filter(s -> s.fundo().equals(fundo)).findFirst().orElseThrow();
    }

    private static BigDecimal somaRecebimentosDeCota(String fundo) {
        return secao(fundo).lancamentos().stream().filter(l -> l.enriquecimento().recebimentoCota())
                .map(LancamentoFluxo::credito).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void contaEmDuasLinhas() {
        var issCaixaGordura = fluxo.secoes().getFirst().lancamentos().stream()
                .filter(l -> "1465".equals(l.contaCodigo())).findFirst().orElseThrow();
        assertThat(issCaixaGordura.contaNome()).isEqualTo("CAIXA GORDURA/FOSSA");
        assertThat(issCaixaGordura.enriquecimento().notaFiscal()).isEqualTo("1002194");
    }

    @Test
    void resultadoCompletoCabeNoContratoDaFila() {
        var arquivo = new br.com.condominioauditoria.rag.mensagens.ArquivoRecebido(1, java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "BALANCETE", "fluxo.pdf", "x/fluxo.pdf",
                "a".repeat(64));
        byte[] json = new br.com.condominioauditoria.rag.mensagens.ContratoMensagens().escrever(
                br.com.condominioauditoria.rag.mensagens.ResultadoProcessamento.concluido(arquivo,
                        "fluxo-caixa-protest", 30, fluxo, ConferenciaFluxo.conferir(fluxo)));
        assertThat(json.length).isGreaterThan(10_000);
    }
}
