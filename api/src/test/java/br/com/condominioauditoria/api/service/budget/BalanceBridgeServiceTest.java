package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** RF-13.6: the period of the balance bridge must be inside the fiscal year of the budget (contract: 400). */
class BalanceBridgeServiceTest {

    private final BudgetScenario scenario = new BudgetScenario();

    @Test
    void periodStartingBeforeTheFiscalYearIsRejected() {
        // D5: no budget is valid in April/2026 (the fiscal year starts in May)
        scenario.confirmedBudget();

        assertThatThrownBy(() -> scenario.bridge.get(scenario.condominiumId, "all", "2026-04", "2026-10"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo("O período da ponte (2026-04 a 2026-10) deve estar dentro do"
                            + " exercício da PO (2026-05 a 2027-04)");
                });
    }

    @Test
    void periodEndingAfterTheFiscalYearIsRejected() {
        scenario.confirmedBudget();

        assertThatThrownBy(() -> scenario.bridge.get(scenario.condominiumId, "all", "2026-05", "2027-05"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void periodInsideTheFiscalYearIsAccepted() {
        scenario.confirmedBudget();

        var r = scenario.bridge.get(scenario.condominiumId, "all", "2026-05", "2026-10");

        assertThat(r.from()).isEqualTo("2026-05");
        assertThat(r.to()).isEqualTo("2026-10");
    }
}
