package br.com.condominioauditoria.api.orcamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.orcamento.AvaliacaoLeituraPo.Classificacao;
import br.com.condominioauditoria.api.orcamento.AvaliacaoLeituraPo.ConferenciaAvaliada;
import br.com.condominioauditoria.api.orcamento.AvaliacaoLeituraPo.ConferenciaPo;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-03.1.2: o que as conferências da PO significam no backend, com a tolerância de arredondamento. */
class AvaliacaoLeituraPoTest {

    private static final BigDecimal UM_CENTAVO = new BigDecimal("0.01");
    private final PrevisaoOrcamentaria previsao = new PrevisaoOrcamentaria(UUID.randomUUID(), UUID.randomUUID(),
            "e".repeat(64));

    @Test
    void poDoPilotoComDiferencasDeUmCentavoFicaLidaComAvisos() {
        PoDoPiloto po = PoDoPiloto.padrao();
        EstruturaPo estrutura = EstruturaPo.de(po.linhasGravadas(previsao));

        var resultado = AvaliacaoLeituraPo.avaliar(estrutura, po.conferenciasParaAvaliacao(), UM_CENTAVO);

        assertThat(resultado.estado()).isEqualTo(EstadoPrevisao.LIDA);
        assertThat(resultado.divergencias()).isEmpty();
        assertThat(resultado.arredondamentos()).containsExactly(
                "1.3 SERVIÇOS - CONTRATOS EFETIVOS impresso 336.274,17; soma das linhas 336.274,18; diferença de 0,01"
                        + " tratada como arredondamento; os cálculos usam a soma das linhas",
                "1.9 Fundos do Condomínio impresso 22.581,01; soma das linhas 22.581,00; diferença de 0,01"
                        + " tratada como arredondamento; os cálculos usam a soma das linhas");
        assertThat(resultado.temCodigoRepetido()).isTrue();
    }

    @Test
    void previstoDoMesUsaASomaDasLinhas() {
        EstruturaPo estrutura = EstruturaPo.de(PoDoPiloto.padrao().linhasGravadas(previsao));

        assertThat(estrutura.total().getOrcado()).isEqualByComparingTo("474201.13");
        assertThat(estrutura.previstoMesImpresso()).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("451620.12"));
        // 1.3 soma 0,01 a mais nas linhas do que o subtotal impresso
        assertThat(estrutura.previstoMesPelasLinhas()).isEqualByComparingTo("451620.13");
        assertThat(estrutura.fundos()).hasValueSatisfying(f -> {
            assertThat(f.linha().getCodigoImpresso()).isEqualTo("1.9");
            assertThat(f.linhas()).extracting(LinhaPo::getCodigoImpresso).containsExactly("1.9.1", "1.9.2");
        });
    }

    @Test
    void semToleranciaAsDiferencasDeUmCentavoSaoDivergencia() {
        PoDoPiloto po = PoDoPiloto.padrao();

        var resultado = AvaliacaoLeituraPo.avaliar(EstruturaPo.de(po.linhasGravadas(previsao)),
                po.conferenciasParaAvaliacao(), new BigDecimal("0.00"));

        assertThat(resultado.estado()).isEqualTo(EstadoPrevisao.LIDA_COM_DIVERGENCIA);
        assertThat(resultado.divergencias()).hasSize(2);
        assertThat(resultado.arredondamentos()).isEmpty();
    }

    @Test
    void subtotalImpressoErradoNaOrigemEhDivergenciaComAsDuasSomas() {
        PoDoPiloto po = PoDoPiloto.padrao().comSubtotalPessoal("69193.00");

        var resultado = AvaliacaoLeituraPo.avaliar(EstruturaPo.de(po.linhasGravadas(previsao)),
                po.conferenciasParaAvaliacao(), UM_CENTAVO);

        assertThat(resultado.estado()).isEqualTo(EstadoPrevisao.LIDA_COM_DIVERGENCIA);
        assertThat(resultado.divergencias()).contains("1.1 PESSOAL impresso 69.193,00; soma das linhas 69.193,86",
                "Total impresso 474.201,13; soma dos grupos 474.200,27");
        assertThat(resultado.arredondamentos()).hasSize(2);
    }

    @Test
    void codigoRepetidoSozinhoNaoEhDivergencia() {
        EstruturaPo estrutura = EstruturaPo.de(PoDoPiloto.padrao().linhasGravadas(previsao));
        var conferencias = List.of(new ConferenciaPo("CODIGO_REPETIDO", "x", false, "1.3.2 aparece 2 vezes"));

        var resultado = AvaliacaoLeituraPo.avaliar(estrutura, conferencias, UM_CENTAVO);

        assertThat(resultado.estado()).isEqualTo(EstadoPrevisao.LIDA);
        assertThat(resultado.conferencias()).extracting(ConferenciaAvaliada::classificacao)
                .containsExactly(Classificacao.CODIGO_REPETIDO);
    }

    @Test
    void falhaQueASomaRefeitaNaoConfirmaEhDivergencia() {
        EstruturaPo estrutura = EstruturaPo.de(PoDoPiloto.padrao().linhasGravadas(previsao));
        // O grupo 1.1 bate pelas linhas gravadas, mas o rag apontou falha: não é arredondamento
        var conferencias = List.of(new ConferenciaPo("SUBTOTAL_GRUPO", "1.1", false, "1.1 PESSOAL: não bate"));

        var resultado = AvaliacaoLeituraPo.avaliar(estrutura, conferencias, UM_CENTAVO);

        assertThat(resultado.estado()).isEqualTo(EstadoPrevisao.LIDA_COM_DIVERGENCIA);
    }

    @Test
    void conferenciaDesconhecidaQueFalhouEhDivergencia() {
        EstruturaPo estrutura = EstruturaPo.de(PoDoPiloto.padrao().linhasGravadas(previsao));
        var conferencias = List.of(new ConferenciaPo("LINHA_SEM_GRUPO", "x", false, "linhas antes do primeiro grupo: 1.0.1"));

        assertThat(AvaliacaoLeituraPo.avaliar(estrutura, conferencias, UM_CENTAVO).estado())
                .isEqualTo(EstadoPrevisao.LIDA_COM_DIVERGENCIA);
    }

    @Test
    void dinheiroNoFormatoBrasileiro() {
        assertThat(DinheiroBr.formatar(new BigDecimal("474201.13"))).isEqualTo("474.201,13");
        assertThat(DinheiroBr.formatar(new BigDecimal("0.01"))).isEqualTo("0,01");
        assertThat(DinheiroBr.formatar(new BigDecimal("-1585.1"))).isEqualTo("-1.585,10");
        assertThat(DinheiroBr.formatar(new BigDecimal("999"))).isEqualTo("999,00");
    }
}
