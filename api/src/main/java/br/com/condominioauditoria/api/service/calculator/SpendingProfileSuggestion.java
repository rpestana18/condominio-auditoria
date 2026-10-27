package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import br.com.condominioauditoria.api.model.enums.SpendingProfile;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Suggested spending profile of a budget line (RF-13.14; ADR 0009, Decision 2). <b>Pure function</b> with fixed rules,
 * in this order, no AI (works with the AI mode off):
 * <ol>
 * <li>the "Rateio à parte" mark → {@link SpendingProfile#SEPARATE_APPORTIONMENT};</li>
 * <li>description (or account name) with "provisão", "férias", "rescis" or "13" (13º salário) →
 * {@link SpendingProfile#PROVISION};</li>
 * <li>Observações with "anual" → {@link SpendingProfile#ANNUAL};</li>
 * <li>months of the year in the description or in Observações ("meses nov à fev", "nov a fev") →
 * {@link SpendingProfile#SEASONAL}. A month followed by a year ("jan 26/abr 26", "out/25") is the period of an average,
 * not a season, and does not count;</li>
 * <li>a group of acquisitions, materials or occasional services → {@link SpendingProfile#OCCASIONAL}. The group name is
 * the one read in the budget itself (1.5 "AQUISIÇÃO DE BENS", 1.7 "MATERIAIS/SUPRIMENTOS" and 1.8 "SERVIÇOS EVENTUAIS"
 * in the pilot), never the group number;</li>
 * <li>everything else → {@link SpendingProfile#RECURRING}.</li>
 * </ol>
 * The account code at the start of the account column ("1553 - Férias") is ignored, so an account numbered 13xx is not a
 * provision. The suggestion never changes the planned amount; the Admin confirms it (P9).
 */
public final class SpendingProfileSuggestion {

    /** Version of these rules; goes in the reason of every suggestion. 2: ADR 0009 (recurring, occasional, seasonal). */
    public static final String VERSION = "2";

    public record Suggestion(SpendingProfile profile, String reason) {
    }

    private static final Pattern ANNUAL = Pattern.compile("\\banual\\b");
    private static final Pattern PROVISION = Pattern.compile("\\bprovis|\\bferias\\b|rescis|(?<!\\d)13(?!\\d)");
    private static final String MONTH = "(jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez)[a-z]*";
    /** "meses nov à fev", "mês de dezembro": the word "meses"/"mês" followed by a month name. */
    private static final Pattern MONTHS_WORD = Pattern.compile("\\bmes(es)?\\b(\\s+de)?\\s+" + MONTH + "\\b(?!\\s*/?\\s*\\d)");
    /** "nov a fev", "nov-fev", "nov até fev", with no year after the months. */
    private static final Pattern MONTH_RANGE = Pattern.compile("\\b" + MONTH + "\\s*(a|-|ate)\\s*" + MONTH
            + "\\b(?!\\s*/?\\s*\\d)");
    private static final Pattern OCCASIONAL_GROUP = Pattern.compile("aquisic|materia|eventua");
    private static final Pattern ACCOUNT_CODE = Pattern.compile("^\\s*\\d+\\s*-\\s*");

    private SpendingProfileSuggestion() {
    }

    /** The suggestion of every expense line of the budget (groups 1.1 to 1.8), by line id. */
    public static Map<UUID, Suggestion> suggestAll(BudgetStructure structure) {
        Map<UUID, Suggestion> m = new LinkedHashMap<>();
        for (BudgetStructure.Group g : structure.groupsWithoutFunds()) {
            g.lines().forEach(l -> m.put(l.getId(), suggest(l, g.line().getDescription())));
        }
        structure.ungrouped().forEach(l -> m.putIfAbsent(l.getId(), suggest(l, null)));
        return m;
    }

    /**
     * @param groupName description of the line's group as read in the budget (e.g. "AQUISIÇÃO DE BENS"); null when the
     *     line has no group
     */
    public static Suggestion suggest(BudgetLine line, String groupName) {
        if (line.getMark() == BudgetLineMark.SEPARATE_APPORTIONMENT) {
            return new Suggestion(SpendingProfile.SEPARATE_APPORTIONMENT, reason("marca \"Rateio à parte\" na PO"));
        }
        String account = normalize(accountName(line.getAccount()));
        String description = normalize(line.getDescription());
        if (PROVISION.matcher(description).find()) {
            return new Suggestion(SpendingProfile.PROVISION, reason("descrição: \"" + line.getDescription().trim()
                    + "\""));
        }
        if (PROVISION.matcher(account).find()) {
            return new Suggestion(SpendingProfile.PROVISION, reason("conta da PO: \"" + line.getAccount().trim() + "\""));
        }
        String notes = normalize(line.getNotes());
        if (ANNUAL.matcher(notes).find()) {
            return new Suggestion(SpendingProfile.ANNUAL, reason("Observações: \"" + line.getNotes().trim() + "\""));
        }
        if (season(description)) {
            return new Suggestion(SpendingProfile.SEASONAL, reason("meses do ano na descrição: \""
                    + line.getDescription().trim() + "\""));
        }
        if (season(notes)) {
            return new Suggestion(SpendingProfile.SEASONAL, reason("meses do ano nas Observações: \""
                    + line.getNotes().trim() + "\""));
        }
        if (groupName != null && OCCASIONAL_GROUP.matcher(normalize(groupName)).find()) {
            return new Suggestion(SpendingProfile.OCCASIONAL, reason("grupo da PO: \"" + groupName.trim() + "\""));
        }
        return new Suggestion(SpendingProfile.RECURRING,
                reason("nenhuma regra de rateio à parte, provisão, anual, sazonal ou eventual"));
    }

    private static boolean season(String text) {
        return MONTHS_WORD.matcher(text).find() || MONTH_RANGE.matcher(text).find();
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
