package br.com.condominioauditoria.api.config.properties;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parameters of the budget reading (block "condominio.budget" of application.yml).
 *
 * @param roundingTolerance largest difference (in reais) between the printed total and the sum that counts as
 *     rounding at the source: the check becomes a warning and does not mark the budget "read with discrepancy".
 *     Calculations always use the sum of the lines. The value lives only in application.yml (pending the user's
 *     confirmation; 0.00 turns the tolerance off).
 */
@ConfigurationProperties(prefix = "condominio.budget")
public record BudgetProperties(BigDecimal roundingTolerance) {

    public BudgetProperties {
        if (roundingTolerance == null || roundingTolerance.signum() < 0) {
            throw new IllegalArgumentException("Informe condominio.budget.rounding-tolerance (zero ou mais)");
        }
        roundingTolerance = roundingTolerance.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }
}
