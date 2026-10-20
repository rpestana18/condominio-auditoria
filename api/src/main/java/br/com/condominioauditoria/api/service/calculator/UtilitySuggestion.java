package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.model.enums.UtilityType;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Suggests the utility of a cash flow account by its name (RF-12.1). Only a suggestion: the Manager or the Admin
 * confirms it before the account enters the charts. Accounts of adjustments between utility funds ("outros débitos
 * concessionárias") and water tank services ("caixa d'água") are not consumption bills and get no suggestion.
 */
public final class UtilitySuggestion {

    private static final Pattern ACCENTS = Pattern.compile("\\p{M}");
    private static final Pattern GAS = Pattern.compile("\\bGAS\\b");
    private static final Pattern LIGHT = Pattern.compile("\\bLUZ\\b");

    private UtilitySuggestion() {
    }

    public static Optional<UtilityType> suggest(String accountName) {
        if (accountName == null || accountName.isBlank()) {
            return Optional.empty();
        }
        String name = normalize(accountName);
        if (name.contains("CONCESSIONARIA")) {
            return Optional.empty();
        }
        if (name.contains("ESGOTO") || (name.contains("AGUA") && !name.contains("CAIXA"))) {
            return Optional.of(UtilityType.WATER);
        }
        if (name.contains("ENERGIA") || name.contains("ELETRICIDADE") || LIGHT.matcher(name).find()) {
            return Optional.of(UtilityType.ELECTRICITY);
        }
        if (GAS.matcher(name).find()) {
            return Optional.of(UtilityType.GAS);
        }
        return Optional.empty();
    }

    static String normalize(String text) {
        return ACCENTS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("").toUpperCase(Locale.ROOT);
    }
}
