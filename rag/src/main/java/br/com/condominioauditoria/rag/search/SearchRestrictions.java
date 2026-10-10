package br.com.condominioauditoria.rag.search;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts from the question the parts that, in the keyword search, are required or forbidden: quoted phrases and
 * terms negated with "-" (websearch_to_tsquery syntax). In the hybrid search, the candidates from the vector side must
 * respect them too; otherwise "salário -transporte" would bring "Vale Transporte" through the vectors.
 *
 * Simplification: with "ou" between phrases, all phrases become required on the vector side (more restrictive).
 */
public final class SearchRestrictions {

    /** Quoted phrase, negated or not. Unclosed quotes do not count as a phrase. */
    private static final Pattern PHRASE = Pattern.compile("(?<![^\\s])(-?)\"([^\"]*)\"");
    /** Negated term: "-" at the start of the word (does not catch "IGP-M"). */
    private static final Pattern NEGATED = Pattern.compile("(?<![^\\s])-([^\\s\"-][^\\s\"]*)");

    private SearchRestrictions() {
    }

    /** Text in websearch_to_tsquery format with the restrictions only, or null if the question has none. */
    public static String extract(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        var rest = new StringBuilder();
        Matcher phrase = PHRASE.matcher(text);
        int end = 0;
        while (phrase.find()) {
            rest.append(text, end, phrase.start()).append(' ');
            end = phrase.end();
            String content = phrase.group(2).replace('"', ' ').strip();
            if (!content.isEmpty()) {
                parts.add(phrase.group(1) + "\"" + content + "\"");
            }
        }
        rest.append(text.substring(end));
        Matcher negated = NEGATED.matcher(rest);
        while (negated.find()) {
            parts.add("-" + negated.group(1));
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }
}
