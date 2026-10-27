package br.com.condominioauditoria.api.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** RF-13.17 (D2): the suggested end of the fiscal year is the first February after the start. */
class FiscalYearEndSuggestionTest {

    @Test
    void firstFebruaryAfterTheStart() {
        assertThat(FiscalYearEndSuggestion.suggest(YearMonth.of(2026, 5))).isEqualTo(YearMonth.of(2027, 2));
        assertThat(FiscalYearEndSuggestion.suggest(YearMonth.of(2026, 3))).isEqualTo(YearMonth.of(2027, 2));
        assertThat(FiscalYearEndSuggestion.suggest(YearMonth.of(2026, 1))).isEqualTo(YearMonth.of(2026, 2));
        assertThat(FiscalYearEndSuggestion.suggest(YearMonth.of(2026, 2))).isEqualTo(YearMonth.of(2027, 2));
        assertThat(FiscalYearEndSuggestion.suggest(null)).isNull();
    }
}
