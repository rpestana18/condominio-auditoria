package br.com.condominioauditoria.api.modulo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.modulo.CustoUso.CustoDoPeriodo;
import br.com.condominioauditoria.api.modulo.CustoUso.PrecoModelo;
import br.com.condominioauditoria.api.modulo.CustoUso.TokensPorModelo;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Custo estimado (RF-09.7): tokens × preço do catálogo, BigDecimal exato, 2 casas só nos totais, determinístico. */
class CustoUsoTest {

    private static final Map<String, PrecoModelo> PRECOS = Map.of(
            "anthropic/claude-sonnet-5-5", new PrecoModelo(new BigDecimal("2.00"), new BigDecimal("10.00")),
            "anthropic/claude-haiku-4-5", new PrecoModelo(new BigDecimal("1.00"), new BigDecimal("5.00")));

    @Test
    void custoExatoPorModelo() {
        // 1.234 × 2,00 / 1e6 + 567 × 10,00 / 1e6 = 0,002468 + 0,00567
        assertThat(CustoUso.exato(1234, 567, PRECOS.get("anthropic/claude-sonnet-5-5")))
                .isEqualByComparingTo("0.008138");
    }

    @Test
    void arredondaSoNoTotalNaoEmCadaRegistro() {
        // Cada pergunta custa US$ 0,004 (arredondada sozinha seria 0,00); três somam 0,012 = US$ 0,01
        var pergunta = new TokensPorModelo("2026-10", Modulos.ASSISTENTE, FuncaoUso.PERGUNTA, "anthropic",
                "claude-sonnet-5-5", 2000, 0);
        var linhas = List.of(pergunta, pergunta, pergunta);

        CustoDoPeriodo custo = CustoUso.calcular(linhas, PRECOS);

        assertThat(custo.total()).isEqualTo(new BigDecimal("0.01"));
        assertThat(custo.porMes()).containsEntry("2026-10|ASSISTENTE|pergunta", new BigDecimal("0.01"));
        assertThat(custo.modelosSemPreco()).isEmpty();
    }

    @Test
    void somaModelosDiferentesEMesesSeparados() {
        var linhas = List.of(
                new TokensPorModelo("2026-10", Modulos.ASSISTENTE, FuncaoUso.PERGUNTA, "anthropic",
                        "claude-sonnet-5-5", 1_000_000, 100_000), // 2,00 + 1,00
                new TokensPorModelo("2026-10", Modulos.ASSISTENTE, FuncaoUso.PERGUNTA, "anthropic",
                        "claude-haiku-4-5", 500_000, 0), // 0,50
                new TokensPorModelo("2026-11", Modulos.ASSISTENTE, FuncaoUso.PERGUNTA, "anthropic",
                        "claude-haiku-4-5", 3, 3)); // 0,000018

        CustoDoPeriodo custo = CustoUso.calcular(linhas, PRECOS);

        assertThat(custo.porMes()).containsEntry("2026-10|ASSISTENTE|pergunta", new BigDecimal("3.50"))
                .containsEntry("2026-11|ASSISTENTE|pergunta", new BigDecimal("0.00"));
        assertThat(custo.porFuncao()).containsEntry("ASSISTENTE|pergunta", new BigDecimal("3.50"));
        assertThat(custo.total()).isEqualTo(new BigDecimal("3.50"));
        assertThat(CustoUso.calcular(linhas, PRECOS)).isEqualTo(custo); // mesmo insumo, mesmo resultado
    }

    @Test
    void modeloSemPrecoDeixaOGrupoEOTotalSemValor() {
        var linhas = List.of(
                new TokensPorModelo("2026-10", Modulos.ASSISTENTE, FuncaoUso.PERGUNTA, "anthropic",
                        "claude-sonnet-5-5", 1000, 0),
                new TokensPorModelo("2026-10", Modulos.ASSISTENTE, FuncaoUso.PERGUNTA, "anthropic", "modelo-antigo",
                        1000, 0));

        CustoDoPeriodo custo = CustoUso.calcular(linhas, PRECOS);

        assertThat(custo.porMes().get("2026-10|ASSISTENTE|pergunta")).isNull();
        assertThat(custo.porMes()).containsKey("2026-10|ASSISTENTE|pergunta");
        assertThat(custo.total()).isNull();
        assertThat(custo.modelosSemPreco()).containsExactly("anthropic/modelo-antigo");
    }

    @Test
    void semTokensNaoHaCusto() {
        CustoDoPeriodo custo = CustoUso.calcular(List.of(new TokensPorModelo("2026-10", Modulos.ASSISTENTE,
                FuncaoUso.BUSCA_DOCUMENTOS, null, null, 0, 0)), PRECOS);

        assertThat(custo.porMes()).isEmpty();
        assertThat(custo.total()).isEqualTo(new BigDecimal("0.00"));
    }
}
