package br.com.condominioauditoria.api.service.audit.rule;

import br.com.condominioauditoria.api.model.enums.Severity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Rule "reserve fund of the budget above the cap" (Conv. 20.1; RF-03.1.3; rule matrix of RF-03.1). Declarative and
 * versioned: changing the rule means raising {@link #VERSION}. The cap comes from the condominium's {@link #PARAMETER}
 * parameter, with validity. Exact comparison, no rounding: there is a finding if {@code reserve × 100 > monthly budget
 * × cap}.
 */
public final class ReserveFundCapRule {

    public static final String CODE = "RESERVE_FUND_ABOVE_CAP";
    public static final String VERSION = "1";
    public static final String PARAMETER = "RESERVE_FUND_CAP_PERCENT";
    public static final Severity SEVERITY = Severity.WARNING;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * @param percentage reserve over the monthly budget, with 10 decimals (shown with 1)
     */
    public record Assessment(boolean aboveCap, BigDecimal percentage, BigDecimal cap) {

        public String displayPercentage() {
            return percentage.setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + "%";
        }

        public String displayCap() {
            return cap.stripTrailingZeros().toPlainString().replace('.', ',') + "%";
        }
    }

    private ReserveFundCapRule() {
    }

    /** Empty when the monthly budget is not positive (there is no base for the percentage). */
    public static Optional<Assessment> assess(BigDecimal monthlyReserve, BigDecimal monthlyBudget,
            BigDecimal capPercentage) {
        if (monthlyBudget == null || monthlyBudget.signum() <= 0) {
            return Optional.empty();
        }
        boolean above = monthlyReserve.multiply(HUNDRED).compareTo(monthlyBudget.multiply(capPercentage)) > 0;
        BigDecimal percentage = monthlyReserve.multiply(HUNDRED).divide(monthlyBudget, 10, RoundingMode.HALF_UP);
        return Optional.of(new Assessment(above, percentage, capPercentage));
    }
}
