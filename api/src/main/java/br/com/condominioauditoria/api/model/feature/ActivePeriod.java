package br.com.condominioauditoria.api.model.feature;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Period in which a feature was enabled in a condominium (RF-10.6), calculated from the activation trail. start is null
 * only when the feature is enabled by default (before any event); end null = still enabled. No billing amount is
 * calculated in this phase.
 */
public record ActivePeriod(String feature, Instant start, Instant end, String enabledBy, String enableReason,
        String disabledBy, String disableReason) {

    /**
     * Walks the events in chronological order: enabling opens a period, disabling closes the open one. The same set of
     * events always gives the same periods.
     *
     * @param enabledByDefault state before the first event (catalog default)
     */
    public static List<ActivePeriod> calculate(String feature, boolean enabledByDefault, List<FeatureEvent> events) {
        List<ActivePeriod> periods = new ArrayList<>();
        boolean enabled = enabledByDefault;
        Instant start = null;
        String enabledBy = null;
        String enableReason = null;
        for (FeatureEvent e : events) {
            if (e.isEnabledAfter() == enabled) {
                continue; // the trail never records an event without a change; if one shows up, it changes nothing
            }
            if (e.isEnabledAfter()) {
                start = e.getOccurredAt();
                enabledBy = e.getUsername();
                enableReason = e.getReason();
            } else {
                periods.add(new ActivePeriod(feature, start, e.getOccurredAt(), enabledBy, enableReason,
                        e.getUsername(), e.getReason()));
                start = null;
                enabledBy = null;
                enableReason = null;
            }
            enabled = e.isEnabledAfter();
        }
        if (enabled) {
            periods.add(new ActivePeriod(feature, start, null, enabledBy, enableReason, null, null));
        }
        return List.copyOf(periods);
    }

    /** Overlaps the interval [from, to)? An open period lasts until now. */
    public boolean overlaps(Instant from, Instant to) {
        boolean startedBeforeEnd = start == null || start.isBefore(to);
        boolean endedAfterStart = end == null || !end.isBefore(from);
        return startedBeforeEnd && endedAfterStart;
    }
}
