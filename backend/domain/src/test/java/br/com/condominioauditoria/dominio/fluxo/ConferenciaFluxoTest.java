package br.com.condominioauditoria.dominio.fluxo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConferenciaFluxoTest {

    private static final LocalDate DIA = LocalDate.of(2026, 9, 1);

    private static LancamentoFluxo lancamento(String credito, String debito, String saldo) {
        return new LancamentoFluxo(1, 1, DIA, "1062", "MATERIAL DE LIMPEZA", "1", "COMPRA",
                new BigDecimal(credito), new BigDecimal(debito), new BigDecimal(saldo),
                new LancamentoFluxo.Enriquecimento(null, null, null, false));
    }

    private static FluxoDeCaixa fluxo(String saldoImpresso) {
        var secao = new SecaoFundo("CONDOMÍNIO", new BigDecimal("100.00"),
                List.of(lancamento("50.00", "0.00", "150.00"), lancamento("0.00", "30.00", saldoImpresso)),
                new BigDecimal("50.00"), new BigDecimal("30.00"));
        var posicao = new PosicaoFundo("CONDOMÍNIO", new BigDecimal("100.00"), new BigDecimal("50.00"),
                new BigDecimal("30.00"), new BigDecimal("120.00"));
        return new FluxoDeCaixa("TESTE", DIA, DIA.plusDays(29), List.of(secao), List.of(posicao), posicao);
    }

    @Test
    void relatorioCoerentePassaEmTudo() {
        assertThat(ConferenciaFluxo.conferir(fluxo("120.00"))).allMatch(Verificacao::ok);
    }

    @Test
    void saldoImpressoErradoEApontadoComALinha() {
        var resultado = ConferenciaFluxo.conferir(fluxo("125.00"));

        Verificacao saldo = resultado.getFirst();
        assertThat(saldo.ok()).isFalse();
        assertThat(saldo.detalhe()).contains("CONDOMÍNIO, pág. 1").contains("calculado 120,00, impresso 125,00");
    }
}
