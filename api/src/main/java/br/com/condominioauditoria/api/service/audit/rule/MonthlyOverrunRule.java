package br.com.condominioauditoria.api.service.audit.rule;

import br.com.condominioauditoria.api.model.enums.Severity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * The 20% rule (Conv. 16.2; RF-03.1.11; Q23 and Q26), per month, in the Condomínio fund. Declarative and versioned:
 * changing the rule means raising {@link #VERSION}. The limit comes from the condominium's {@link #PARAMETER}
 * parameter, with validity.
 *
 * <p>Overrun = sum of the positive differences, line by line, of the expense lines (Q23); "a realocar" and "sem linha
 * da PO" stay out (Q26). Exact comparison, no rounding: there is a finding if {@code overrun × 100 > monthly budget ×
 * limit}. So 90.324,02 over 451.620,10 (exactly 20%) raises no finding, and 90.324,03 does.
 */
public final class MonthlyOverrunRule {

    public static final String CODE = "MONTHLY_OVERRUN_ABOVE_LIMIT";
    public static final String VERSION = "1";
    public static final String PARAMETER = "MONTHLY_OVERRUN_LIMIT_PERCENT";
    public static final Severity SEVERITY = Severity.CRITICAL;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * @param percentage overrun over the monthly budget, with 10 decimals (shown with 1)
     *
     * @param limit limit in reais (monthly budget × limit %), rounded to cents only for display
     */
    public record Assessment(boolean aboveLimit, BigDecimal percentage, BigDecimal limit, BigDecimal limitPercentage) {
    }

    private MonthlyOverrunRule() {
    }

    /** Empty when the monthly budget is not positive (there is no base for the percentage). */
    public static Optional<Assessment> assess(BigDecimal overrun, BigDecimal monthlyBudget,
            BigDecimal limitPercentage) {
        if (monthlyBudget == null || monthlyBudget.signum() <= 0) {
            return Optional.empty();
        }
        boolean above = overrun.multiply(HUNDRED).compareTo(monthlyBudget.multiply(limitPercentage)) > 0;
        BigDecimal percentage = overrun.multiply(HUNDRED).divide(monthlyBudget, 10, RoundingMode.HALF_UP);
        BigDecimal limit = monthlyBudget.multiply(limitPercentage).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return Optional.of(new Assessment(above, percentage, limit, limitPercentage));
    }
}
