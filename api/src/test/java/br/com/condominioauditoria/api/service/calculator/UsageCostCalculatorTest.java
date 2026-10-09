package br.com.condominioauditoria.api.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.model.enums.UsageFunction;
import br.com.condominioauditoria.api.service.calculator.UsageCostCalculator.ModelPrice;
import br.com.condominioauditoria.api.service.calculator.UsageCostCalculator.ModelTokens;
import br.com.condominioauditoria.api.service.calculator.UsageCostCalculator.PeriodCost;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Estimated cost (RF-09.7): tokens × catalog price, exact BigDecimal, 2 decimals only on totals, deterministic. */
class UsageCostCalculatorTest {

    private static final Map<String, ModelPrice> PRICES = Map.of(
            "anthropic/claude-sonnet-5-5", new ModelPrice(new BigDecimal("2.00"), new BigDecimal("10.00")),
            "anthropic/claude-haiku-4-5", new ModelPrice(new BigDecimal("1.00"), new BigDecimal("5.00")));

    @Test
    void exactCostPerModel() {
        // 1.234 × 2,00 / 1e6 + 567 × 10,00 / 1e6 = 0,002468 + 0,00567
        assertThat(UsageCostCalculator.exact(1234, 567, PRICES.get("anthropic/claude-sonnet-5-5")))
                .isEqualByComparingTo("0.008138");
    }

    @Test
    void roundsOnlyTheTotalNotEachRecord() {
        // Each question costs US$ 0.004 (rounded alone it would be 0.00); three add up to 0.012 = US$ 0.01
        var question = new ModelTokens("2026-10", FeatureService.ASSISTANT, UsageFunction.PERGUNTA, "anthropic",
                "claude-sonnet-5-5", 2000, 0);
        var rows = List.of(question, question, question);

        PeriodCost cost = UsageCostCalculator.calculate(rows, PRICES);

        assertThat(cost.total()).isEqualTo(new BigDecimal("0.01"));
        assertThat(cost.byMonth()).containsEntry("2026-10|ASSISTENTE|pergunta", new BigDecimal("0.01"));
        assertThat(cost.modelsWithoutPrice()).isEmpty();
    }

    @Test
    void sumsDifferentModelsAndSeparateMonths() {
        var rows = List.of(
                new ModelTokens("2026-10", FeatureService.ASSISTANT, UsageFunction.PERGUNTA, "anthropic",
                        "claude-sonnet-5-5", 1_000_000, 100_000), // 2,00 + 1,00
                new ModelTokens("2026-10", FeatureService.ASSISTANT, UsageFunction.PERGUNTA, "anthropic",
                        "claude-haiku-4-5", 500_000, 0), // 0,50
                new ModelTokens("2026-11", FeatureService.ASSISTANT, UsageFunction.PERGUNTA, "anthropic",
                        "claude-haiku-4-5", 3, 3)); // 0,000018

        PeriodCost cost = UsageCostCalculator.calculate(rows, PRICES);

        assertThat(cost.byMonth()).containsEntry("2026-10|ASSISTENTE|pergunta", new BigDecimal("3.50"))
                .containsEntry("2026-11|ASSISTENTE|pergunta", new BigDecimal("0.00"));
        assertThat(cost.byFunction()).containsEntry("ASSISTENTE|pergunta", new BigDecimal("3.50"));
        assertThat(cost.total()).isEqualTo(new BigDecimal("3.50"));
        assertThat(UsageCostCalculator.calculate(rows, PRICES)).isEqualTo(cost); // mesmo insumo, mesmo resultado
    }

    @Test
    void modelWithoutPriceLeavesTheGroupAndTotalWithoutValue() {
        var rows = List.of(
                new ModelTokens("2026-10", FeatureService.ASSISTANT, UsageFunction.PERGUNTA, "anthropic",
                        "claude-sonnet-5-5", 1000, 0),
                new ModelTokens("2026-10", FeatureService.ASSISTANT, UsageFunction.PERGUNTA, "anthropic",
                        "modelo-antigo", 1000, 0));

        PeriodCost cost = UsageCostCalculator.calculate(rows, PRICES);

        assertThat(cost.byMonth().get("2026-10|ASSISTENTE|pergunta")).isNull();
        assertThat(cost.byMonth()).containsKey("2026-10|ASSISTENTE|pergunta");
        assertThat(cost.total()).isNull();
        assertThat(cost.modelsWithoutPrice()).containsExactly("anthropic/modelo-antigo");
    }

    @Test
    void withoutTokensThereIsNoCost() {
        PeriodCost cost = UsageCostCalculator.calculate(List.of(new ModelTokens("2026-10", FeatureService.ASSISTANT,
                UsageFunction.BUSCA_DOCUMENTOS, null, null, 0, 0)), PRICES);

        assertThat(cost.byMonth()).isEmpty();
        assertThat(cost.total()).isEqualTo(new BigDecimal("0.00"));
    }
}
