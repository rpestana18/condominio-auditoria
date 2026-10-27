package br.com.condominioauditoria.api.service.calculator;

import java.time.Month;
import java.time.YearMonth;

/**
 * Suggested end of a fiscal year (RF-13.17; ADR 0009, Decision 6). <b>Pure function</b>: the month before the start
 * of the next fiscal year by the "março a março" rule (D2), that is, the first February after the start month. Starting
 * in March, the year runs to February of the next year; starting in May/2026 (the pilot), the end is February/2027.
 * Only a suggestion: the Admin informs the end in the confirmation and can correct it later, with the trail.
 */
public final class FiscalYearEndSuggestion {

    private FiscalYearEndSuggestion() {
    }

    public static YearMonth suggest(YearMonth start) {
        if (start == null) {
            return null;
        }
        YearMonth end = YearMonth.of(start.getYear(), Month.FEBRUARY);
        while (!end.isAfter(start)) {
            end = end.plusYears(1);
        }
        return end;
    }
}
