package br.com.condominioauditoria.api.modulo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.modulo.CustoUso.CustoDoPeriodo;
import br.com.condominioauditoria.api.modulo.CustoUso.PrecoModelo;
import br.com.condominioauditoria.api.modulo.CustoUso.TokensPorModelo;
import br.com.condominioauditoria.api.modulo.ModuloController.UsoDto;
import br.com.condominioauditoria.api.modulo.RegistroUso.ResumoUso;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * GET /uso (contracts/openapi.yaml, UsoDoPeriodo e TotalUso): custoEstimadoUsd ausente sem tokens, nulo com modelo
 * sem preço, texto com 2 casas no resto; custoDisponivel = false sem catálogo, sem nenhum valor de custo.
 */
class UsoJsonTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TotalUso BUSCA = new TotalUso("2026-10", Modulos.ASSISTENTE, FuncaoUso.BUSCA_DOCUMENTOS, 5,
            0, 0, 0, 0);
    private static final TotalUso PERGUNTA = new TotalUso("2026-10", Modulos.ASSISTENTE, FuncaoUso.PERGUNTA, 3,
            1_000_000, 100_000, 0, 0);
    private static final ResumoUso RESUMO = new ResumoUso(UUID.randomUUID(), LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 31), TotalUso.somarPorFuncao(List.of(BUSCA, PERGUNTA)), List.of(BUSCA, PERGUNTA));

    @Test
    void comCatalogoTrazCustoPorLinhaETotal() throws Exception {
        CustoDoPeriodo custo = CustoUso.calcular(List.of(new TokensPorModelo("2026-10", Modulos.ASSISTENTE,
                FuncaoUso.PERGUNTA, "anthropic", "claude-sonnet-5-5", 1_000_000, 100_000)),
                Map.of("anthropic/claude-sonnet-5-5", new PrecoModelo(new BigDecimal("2.00"), new BigDecimal("10.00"))));

        JsonNode uso = JSON.readTree(JSON.writeValueAsString(UsoDto.de(RESUMO, custo)));

        assertThat(uso.get("custoDisponivel").asBoolean()).isTrue();
        assertThat(uso.get("custoEstimadoTotalUsd").asText()).isEqualTo("3.00");
        assertThat(uso.get("modelosSemPreco")).isEmpty();
        assertThat(uso.get("porMes").get(0).has("custoEstimadoUsd")).isFalse(); // busca: sem tokens
        assertThat(uso.get("porMes").get(1).get("custoEstimadoUsd").asText()).isEqualTo("3.00");
        assertThat(uso.get("porFuncao").get(1).get("custoEstimadoUsd").asText()).isEqualTo("3.00");
    }

    @Test
    void modeloSemPrecoDeixaOCustoNulo() throws Exception {
        CustoDoPeriodo custo = CustoUso.calcular(List.of(new TokensPorModelo("2026-10", Modulos.ASSISTENTE,
                FuncaoUso.PERGUNTA, "anthropic", "modelo-antigo", 1_000_000, 100_000)), Map.of());

        JsonNode uso = JSON.readTree(JSON.writeValueAsString(UsoDto.de(RESUMO, custo)));

        assertThat(uso.get("custoDisponivel").asBoolean()).isTrue();
        assertThat(uso.has("custoEstimadoTotalUsd")).isTrue();
        assertThat(uso.get("custoEstimadoTotalUsd").isNull()).isTrue();
        assertThat(uso.get("porMes").get(1).get("custoEstimadoUsd").isNull()).isTrue();
        assertThat(uso.get("modelosSemPreco").get(0).asText()).isEqualTo("anthropic/modelo-antigo");
    }

    @Test
    void semCatalogoOUsoSaiSemNenhumValorDeCusto() throws Exception {
        JsonNode uso = JSON.readTree(JSON.writeValueAsString(UsoDto.de(RESUMO, null)));

        assertThat(uso.get("custoDisponivel").asBoolean()).isFalse();
        assertThat(uso.has("custoEstimadoTotalUsd")).isFalse();
        assertThat(uso.get("porMes").get(1).has("custoEstimadoUsd")).isFalse();
        assertThat(uso.get("porMes").get(1).get("tokensEntrada").asLong()).isEqualTo(1_000_000);
    }
}
