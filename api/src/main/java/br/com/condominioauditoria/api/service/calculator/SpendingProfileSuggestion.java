package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import br.com.condominioauditoria.api.model.enums.SpendingProfile;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Suggested spending profile of a budget line (RF-13.3; ADR 0007, Decision 2). <b>Pure function</b> with fixed rules,
 * in this order, no AI (works with the AI mode off):
 * <ol>
 * <li>the "Rateio à parte" mark → {@link SpendingProfile#SEPARATE_APPORTIONMENT};</li>
 * <li>Observações with "anual" or "no ano" → {@link SpendingProfile#ANNUAL};</li>
 * <li>account name or description with "férias", "rescis" or "13" (13º salário) → {@link SpendingProfile#PROVISION};</li>
 * <li>everything else → {@link SpendingProfile#MONTHLY}.</li>
 * </ol>
 * The account code at the start of the account column ("1553 - Férias") is ignored, so an account numbered 13xx is not a
 * provision. The suggestion never changes the planned amount.
 */
public final class SpendingProfileSuggestion {

    /** Version of these rules; goes in the reason of every suggestion. */
    public static final String VERSION = "1";

    public record Suggestion(SpendingProfile profile, String reason) {
    }

    private static final Pattern ANNUAL = Pattern.compile("\\banual\\b|\\bno ano\\b");
    private static final Pattern PROVISION = Pattern.compile("\\bferias\\b|rescis|(?<!\\d)13(?!\\d)");
    private static final Pattern ACCOUNT_CODE = Pattern.compile("^\\s*\\d+\\s*-\\s*");

    private SpendingProfileSuggestion() {
    }

    public static Suggestion suggest(BudgetLine line) {
        if (line.getMark() == BudgetLineMark.SEPARATE_APPORTIONMENT) {
            return new Suggestion(SpendingProfile.SEPARATE_APPORTIONMENT, reason("marca \"Rateio à parte\" na PO"));
        }
        String notes = normalize(line.getNotes());
        if (ANNUAL.matcher(notes).find()) {
            return new Suggestion(SpendingProfile.ANNUAL, reason("Observações: \"" + line.getNotes().trim() + "\""));
        }
        String account = normalize(accountName(line.getAccount()));
        String description = normalize(line.getDescription());
        if (PROVISION.matcher(account).find()) {
            return new Suggestion(SpendingProfile.PROVISION, reason("conta da PO: \"" + line.getAccount().trim() + "\""));
        }
        if (PROVISION.matcher(description).find()) {
            return new Suggestion(SpendingProfile.PROVISION, reason("descrição: \"" + line.getDescription().trim()
                    + "\""));
        }
        return new Suggestion(SpendingProfile.MONTHLY, reason("nenhuma regra de provisão, anual ou rateio à parte"));
    }

    private static String reason(String text) {
        return "sugestão (regras v" + VERSION + "): " + text;
    }

    private static String accountName(String account) {
        return account == null ? "" : ACCOUNT_CODE.matcher(account).replaceFirst("");
    }

    /** Lowercase, without accents; "13º" becomes "13o" (compatibility decomposition). */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .trim();
    }
}
