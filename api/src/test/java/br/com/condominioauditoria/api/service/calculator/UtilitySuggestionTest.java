package br.com.condominioauditoria.api.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.model.enums.UtilityType;
import org.junit.jupiter.api.Test;

/** RF-12.1: suggestions by the account name, with the account names of the pilot's September/2026 cash flow. */
class UtilitySuggestionTest {

    @Test
    void pilotUtilityAccountsAreSuggested() {
        assertThat(UtilitySuggestion.suggest("AGUA/ESGOTO - PG")).contains(UtilityType.WATER);
        assertThat(UtilitySuggestion.suggest("ENERGIA ELETRICA - PGTO")).contains(UtilityType.ELECTRICITY);
        assertThat(UtilitySuggestion.suggest("GÁS")).contains(UtilityType.GAS);
        assertThat(UtilitySuggestion.suggest("Conta de luz")).contains(UtilityType.ELECTRICITY);
    }

    @Test
    void adjustmentsWaterTankAndOtherAccountsAreNotSuggested() {
        assertThat(UtilitySuggestion.suggest("OUTROS DEBITOS CONCESSIONARIAS")).isEmpty();
        assertThat(UtilitySuggestion.suggest("CAIXA D'ÁGUA")).isEmpty();
        assertThat(UtilitySuggestion.suggest("TELEFONE FIXO")).isEmpty();
        assertThat(UtilitySuggestion.suggest("ACESSO INTERNET")).isEmpty();
        assertThat(UtilitySuggestion.suggest("GASTOS DIVERSOS")).isEmpty();
        assertThat(UtilitySuggestion.suggest(null)).isEmpty();
    }
}
