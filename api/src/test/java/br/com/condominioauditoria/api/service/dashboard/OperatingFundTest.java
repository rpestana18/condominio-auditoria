package br.com.condominioauditoria.api.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.dto.response.dashboard.FundPeriodResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Numbers from the pilot's September/2026 cash flow (RF-05.1a and RF-05.1b). */
class OperatingFundTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();
    private static final UUID ENERGY = UUID.randomUUID();
    private static final UUID WATER = UUID.randomUUID();

    private static final List<FundPeriodResponse> SEPTEMBER = List.of(
            fund(CONDOMINIUM, "CONDOMÍNIO", "253802.03", "493167.58", "449455.13", "297514.48"),
            fund(ENERGY, "ENERGIA ELÉTRICA", "24475.84", "99615.32", "23721.24", "100369.92"),
            fund(WATER, "ÁGUA/ESGOTO", "25676.03", "130270.60", "144092.31", "11854.32"));

    @Test
    void confirmedFundShowsItsClosingBalance() {
        var operatingFund = DashboardService.operatingFund(CONDOMINIUM, "CONDOMÍNIO", SEPTEMBER).orElseThrow();

        assertThat(operatingFund.confirmed()).isTrue();
        assertThat(operatingFund.fund()).isEqualTo("CONDOMÍNIO");
        assertThat(operatingFund.closingBalance()).isEqualByComparingTo("297514.48");
    }

    @Test
    void withoutConfirmationSuggestsTheFundWithMostInflows() {
        var suggestion = DashboardService.operatingFund(null, null, SEPTEMBER).orElseThrow();

        assertThat(suggestion.confirmed()).isFalse();
        assertThat(suggestion.fundId()).isEqualTo(CONDOMINIUM);
        assertThat(suggestion.closingBalance()).isEqualByComparingTo("297514.48");
    }

    @Test
    void confirmationHoldsEvenWhenAnotherFundHasMoreInflows() {
        var operatingFund = DashboardService.operatingFund(WATER, "ÁGUA/ESGOTO", SEPTEMBER).orElseThrow();

        assertThat(operatingFund.confirmed()).isTrue();
        assertThat(operatingFund.closingBalance()).isEqualByComparingTo("11854.32");
    }

    @Test
    void confirmedFundMissingFromReportHasNoBalance() {
        var operatingFund = DashboardService.operatingFund(UUID.randomUUID(), "CONDOMÍNIO", SEPTEMBER).orElseThrow();

        assertThat(operatingFund.confirmed()).isTrue();
        assertThat(operatingFund.closingBalance()).isNull();
    }

    @Test
    void withoutConfirmationAndInflowsThereIsNoSuggestion() {
        var withoutInflows = List.of(fund(CONDOMINIUM, "CONDOMÍNIO", "100.00", "0.00", "0.00", "100.00"));

        assertThat(DashboardService.operatingFund(null, null, withoutInflows)).isEmpty();
    }

    private static FundPeriodResponse fund(UUID id, String name, String previous, String inflows, String outflows,
            String current) {
        BigDecimal e = new BigDecimal(inflows);
        BigDecimal s = new BigDecimal(outflows);
        return new FundPeriodResponse(id, name, new BigDecimal(previous), e, s, e.subtract(s), new BigDecimal(current));
    }
}
