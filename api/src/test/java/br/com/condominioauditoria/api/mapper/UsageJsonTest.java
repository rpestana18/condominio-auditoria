package br.com.condominioauditoria.api.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.model.enums.UsageFunction;
import br.com.condominioauditoria.api.model.usage.UsageTotal;
import br.com.condominioauditoria.api.service.calculator.UsageCostCalculator;
import br.com.condominioauditoria.api.service.calculator.UsageCostCalculator.ModelPrice;
import br.com.condominioauditoria.api.service.calculator.UsageCostCalculator.ModelTokens;
import br.com.condominioauditoria.api.service.calculator.UsageCostCalculator.PeriodCost;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import br.com.condominioauditoria.api.service.usage.UsageService.UsageSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * GET /uso (contracts/openapi.yaml, UsoDoPeriodo and TotalUso): custoEstimadoUsd absent without tokens, null with a
 * model without price, text with 2 decimals otherwise; custoDisponivel = false without catalog, with no cost value.
 */
class UsageJsonTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UsageTotal SEARCH = new UsageTotal("2026-10", FeatureService.ASSISTANT,
            UsageFunction.BUSCA_DOCUMENTOS, 5, 0, 0, 0, 0);
    private static final UsageTotal QUESTION = new UsageTotal("2026-10", FeatureService.ASSISTANT,
            UsageFunction.PERGUNTA, 3, 1_000_000, 100_000, 0, 0);
    private static final UsageSummary SUMMARY = new UsageSummary(UUID.randomUUID(), LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 31), UsageTotal.sumByFunction(List.of(SEARCH, QUESTION)), List.of(SEARCH, QUESTION));

    @Test
    void withCatalogHasCostPerRowAndTotal() throws Exception {
        PeriodCost cost = UsageCostCalculator.calculate(List.of(new ModelTokens("2026-10", FeatureService.ASSISTANT,
                UsageFunction.PERGUNTA, "anthropic", "claude-sonnet-5-5", 1_000_000, 100_000)),
                Map.of("anthropic/claude-sonnet-5-5", new ModelPrice(new BigDecimal("2.00"), new BigDecimal("10.00"))));

        JsonNode usage = JSON.readTree(JSON.writeValueAsString(UsageMapper.toResponse(SUMMARY, cost)));

        assertThat(usage.get("custoDisponivel").asBoolean()).isTrue();
        assertThat(usage.get("custoEstimadoTotalUsd").asText()).isEqualTo("3.00");
        assertThat(usage.get("modelosSemPreco")).isEmpty();
        assertThat(usage.get("porMes").get(0).has("custoEstimadoUsd")).isFalse(); // search: no tokens
        assertThat(usage.get("porMes").get(1).get("custoEstimadoUsd").asText()).isEqualTo("3.00");
        assertThat(usage.get("porFuncao").get(1).get("custoEstimadoUsd").asText()).isEqualTo("3.00");
    }

    @Test
    void modelWithoutPriceLeavesTheCostNull() throws Exception {
        PeriodCost cost = UsageCostCalculator.calculate(List.of(new ModelTokens("2026-10", FeatureService.ASSISTANT,
                UsageFunction.PERGUNTA, "anthropic", "modelo-antigo", 1_000_000, 100_000)), Map.of());

        JsonNode usage = JSON.readTree(JSON.writeValueAsString(UsageMapper.toResponse(SUMMARY, cost)));

        assertThat(usage.get("custoDisponivel").asBoolean()).isTrue();
        assertThat(usage.has("custoEstimadoTotalUsd")).isTrue();
        assertThat(usage.get("custoEstimadoTotalUsd").isNull()).isTrue();
        assertThat(usage.get("porMes").get(1).get("custoEstimadoUsd").isNull()).isTrue();
        assertThat(usage.get("modelosSemPreco").get(0).asText()).isEqualTo("anthropic/modelo-antigo");
    }

    @Test
    void withoutCatalogUsageHasNoCostValue() throws Exception {
        JsonNode usage = JSON.readTree(JSON.writeValueAsString(UsageMapper.toResponse(SUMMARY, null)));

        assertThat(usage.get("custoDisponivel").asBoolean()).isFalse();
        assertThat(usage.has("custoEstimadoTotalUsd")).isFalse();
        assertThat(usage.get("porMes").get(1).has("custoEstimadoUsd")).isFalse();
        assertThat(usage.get("porMes").get(1).get("tokensEntrada").asLong()).isEqualTo(1_000_000);
    }
}
