package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.api.dto.request.budget.SpendingProfileBatchRequest;
import br.com.condominioauditoria.api.dto.request.budget.SpendingProfileItemRequest;
import br.com.condominioauditoria.api.dto.response.budget.SpendingProfileLineResponse;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.enums.SpendingProfile;
import br.com.condominioauditoria.api.model.enums.SpendingProfileStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** RF-13.3: suggested at the budget confirmation, confirmed by the Admin, every change in the trail. */
class SpendingProfileServiceTest {

    private final BudgetScenario scenario = new BudgetScenario();

    @Test
    void confirmationSuggestsEveryExpenseLineAndNothingIsConfirmed() {
        Budget budget = scenario.confirmedBudget();

        var r = scenario.spendingProfiles.list(scenario.condominiumId, budget.getId());

        assertThat(r.confirmed()).isZero();
        assertThat(r.lines()).hasSize(r.suggested()).allSatisfy(l -> {
            assertThat(l.status()).isEqualTo(SpendingProfileStatus.SUGGESTED);
            // RF-13.14: without confirmation, the suggestion holds ("tipo não confirmado")
            assertThat(l.effectiveProfile()).isEqualTo(l.profile());
            assertThat(l.suggestionChanged()).isFalse();
        });
        assertThat(line(r.lines(), "1.5.1").profile()).isEqualTo(SpendingProfile.OCCASIONAL);
        assertThat(line(r.lines(), "1.1.5").profile()).isEqualTo(SpendingProfile.PROVISION);
        assertThat(line(r.lines(), "1.4.1").profile()).isEqualTo(SpendingProfile.SEPARATE_APPORTIONMENT);
        assertThat(line(r.lines(), "1.6.15").profile()).isEqualTo(SpendingProfile.SEPARATE_APPORTIONMENT);
        assertThat(line(r.lines(), "1.1.1").profile()).isEqualTo(SpendingProfile.RECURRING);
        // Only the expense lines (1.1 to 1.8): no total, group or 1.9 line
        assertThat(r.lines()).extracting(SpendingProfileLineResponse::printedCode).doesNotContain("1", "1.1", "1.9.1");
        assertThat(scenario.spendingProfileEvents).hasSize(r.lines().size())
                .allSatisfy(e -> assertThat(e.getAction()).isEqualTo(SpendingProfileStatus.SUGGESTED));
        // The planned amount does not change
        assertThat(budget.getMonthlyPlanned()).isEqualByComparingTo("451620.13");
    }

    @Test
    void adminConfirmsTheSuggestionOrAnotherProfileWithTrail() {
        Budget budget = scenario.confirmedBudget();
        var vacation = scenario.line(budget, "1.1.5", 0);
        var services = scenario.line(budget, "1.8.1", 0);
        scenario.spendingProfileEvents.clear();

        var r = scenario.spendingProfiles.confirm(scenario.condominiumId, budget.getId(),
                new SpendingProfileBatchRequest(List.of(new SpendingProfileItemRequest(vacation.getId(), null),
                        new SpendingProfileItemRequest(services.getId(), SpendingProfile.ANNUAL)), "conferido"),
                "admin");

        assertThat(r.confirmed()).isEqualTo(2);
        assertThat(line(r.lines(), "1.1.5").effectiveProfile()).isEqualTo(SpendingProfile.PROVISION);
        assertThat(line(r.lines(), "1.8.1").effectiveProfile()).isEqualTo(SpendingProfile.ANNUAL);
        assertThat(line(r.lines(), "1.8.1").suggestedProfile()).isEqualTo(SpendingProfile.RECURRING);
        // P9: a confirmed profile different from the revised suggestion is shown as "a sugestão mudou"
        assertThat(line(r.lines(), "1.8.1").suggestionChanged()).isTrue();
        assertThat(r.suggestionChanged()).isEqualTo(1);
        assertThat(scenario.spendingProfileEvents).hasSize(2).allSatisfy(e -> {
            assertThat(e.getAction()).isEqualTo(SpendingProfileStatus.CONFIRMED);
            assertThat(e.getPreviousStatus()).isEqualTo(SpendingProfileStatus.SUGGESTED);
            assertThat(e.getUsername()).isEqualTo("admin");
            assertThat(e.getReason()).isEqualTo("conferido");
        });
        assertThat(scenario.spendingProfiles.events(scenario.condominiumId, budget.getId())).hasSize(2);

        // The same again changes nothing and records nothing
        scenario.spendingProfileEvents.clear();
        scenario.spendingProfiles.confirm(scenario.condominiumId, budget.getId(), new SpendingProfileBatchRequest(
                List.of(new SpendingProfileItemRequest(vacation.getId(), SpendingProfile.PROVISION)), "conferido"),
                "admin");
        assertThat(scenario.spendingProfileEvents).isEmpty();
    }

    @Test
    void fundLineOrUnconfirmedBudgetIsRejected() {
        Budget budget = scenario.confirmedBudget();
        var reserve = scenario.line(budget, "1.9.1", 0);

        assertThatThrownBy(() -> scenario.spendingProfiles.confirm(scenario.condominiumId, budget.getId(),
                new SpendingProfileBatchRequest(List.of(new SpendingProfileItemRequest(reserve.getId(),
                        SpendingProfile.RECURRING)), null), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(e.getReason()).contains("não é linha de despesa");
                });

        Budget read = scenario.readBudget(PilotBudget.defaults());
        assertThatThrownBy(() -> scenario.spendingProfiles.confirm(scenario.condominiumId, read.getId(),
                new SpendingProfileBatchRequest(List.of(new SpendingProfileItemRequest(
                        scenario.line(read, "1.1.5", 0).getId(), null)), null), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private static SpendingProfileLineResponse line(List<SpendingProfileLineResponse> lines, String code) {
        return lines.stream().filter(l -> l.printedCode().equals(code)).findFirst().orElseThrow();
    }
}
