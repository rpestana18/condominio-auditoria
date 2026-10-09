package br.com.condominioauditoria.api.model.feature;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.service.feature.FeatureService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Active periods calculated from the trail (RF-10.6). */
class ActivePeriodTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();
    private static final Instant ENABLED_AT = Instant.parse("2026-11-01T13:00:00Z");
    private static final Instant DISABLED_AT = Instant.parse("2026-12-15T18:30:00Z");

    @Test
    void enabledInNovemberAndDisabledInDecemberGivesOnePeriodWithWhoEnabledAndDisabled() {
        var periods = ActivePeriod.calculate(FeatureService.ASSISTANT, false, List.of(
                event(false, true, "ana", ENABLED_AT, "Contrato assinado"),
                event(true, false, "bruno", DISABLED_AT, "Fim do teste")));

        assertThat(periods).containsExactly(new ActivePeriod(FeatureService.ASSISTANT, ENABLED_AT, DISABLED_AT, "ana",
                "Contrato assinado", "bruno", "Fim do teste"));
    }

    @Test
    void reenabledOpensASecondPeriodStillOpen() {
        Instant reenabledAt = Instant.parse("2027-02-01T10:00:00Z");
        var periods = ActivePeriod.calculate(FeatureService.ASSISTANT, false, List.of(
                event(false, true, "ana", ENABLED_AT, "a"),
                event(true, false, "ana", DISABLED_AT, "b"),
                event(false, true, "carla", reenabledAt, "c")));

        assertThat(periods).hasSize(2);
        assertThat(periods.get(1).start()).isEqualTo(reenabledAt);
        assertThat(periods.get(1).end()).isNull();
        assertThat(periods.get(1).enabledBy()).isEqualTo("carla");
        assertThat(periods.get(1).disabledBy()).isNull();
    }

    @Test
    void withoutEventsAndDisabledByDefaultThereIsNoPeriod() {
        assertThat(ActivePeriod.calculate(FeatureService.ASSISTANT, false, List.of())).isEmpty();
    }

    @Test
    void featureEnabledByDefaultStartsOpenWithoutStart() {
        var periods = ActivePeriod.calculate("OUTRO", true, List.of(event(true, false, "ana", DISABLED_AT, "x")));

        assertThat(periods).containsExactly(new ActivePeriod("OUTRO", null, DISABLED_AT, null, null, "ana", "x"));
    }

    @Test
    void sameSetOfEventsAlwaysGivesTheSameResult() {
        var events = List.of(event(false, true, "ana", ENABLED_AT, "a"), event(true, false, "ana", DISABLED_AT, "b"));

        assertThat(ActivePeriod.calculate(FeatureService.ASSISTANT, false, events))
                .isEqualTo(ActivePeriod.calculate(FeatureService.ASSISTANT, false, events));
    }

    @Test
    void periodOverlapsTheRequestedInterval() {
        var closed = new ActivePeriod(FeatureService.ASSISTANT, ENABLED_AT, DISABLED_AT, "a", "m", "b", "n");
        var open = new ActivePeriod(FeatureService.ASSISTANT, ENABLED_AT, null, "a", "m", null, null);

        assertThat(closed.overlaps(Instant.parse("2026-12-01T03:00:00Z"), Instant.parse("2027-01-01T03:00:00Z")))
                .isTrue();
        assertThat(closed.overlaps(Instant.parse("2027-01-01T03:00:00Z"), Instant.parse("2027-02-01T03:00:00Z")))
                .isFalse();
        assertThat(closed.overlaps(Instant.parse("2026-10-01T03:00:00Z"), Instant.parse("2026-11-01T03:00:00Z")))
                .isFalse();
        assertThat(open.overlaps(Instant.parse("2030-01-01T03:00:00Z"), Instant.parse("2030-02-01T03:00:00Z")))
                .isTrue();
    }

    private static FeatureEvent event(boolean before, boolean after, String username, Instant at, String reason) {
        return new FeatureEvent(CONDOMINIUM, FeatureService.ASSISTANT, before, after, username, at, reason);
    }
}
