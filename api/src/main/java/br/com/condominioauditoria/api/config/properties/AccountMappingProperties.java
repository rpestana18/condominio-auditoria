package br.com.condominioauditoria.api.config.properties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parameters of the account mapping suggestion by name (block "condominio.budget.account-mapping" of application.yml;
 * ADR 0004, Decision 4). The comparison is plain text, without AI.
 *
 * @param minScore lowest share of common words (0 to 1) that becomes a suggestion
 *
 * @param stopWords words that do not count in the comparison (already without accents, upper case)
 *
 * @param abbreviations abbreviation → whole word (already without accents, upper case), e.g. DESP → DESPESA
 */
@ConfigurationProperties(prefix = "condominio.budget.account-mapping")
public record AccountMappingProperties(BigDecimal minScore, List<String> stopWords, Map<String, String> abbreviations) {

    public AccountMappingProperties {
        if (minScore == null || minScore.signum() <= 0 || minScore.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("condominio.budget.account-mapping.min-score deve estar entre 0 (exclusive) e 1");
        }
        // The configuration may come in lower case (map keys in YAML): the comparison is always upper case
        stopWords = stopWords == null ? List.of()
                : stopWords.stream().map(p -> p.trim().toUpperCase(java.util.Locale.ROOT)).toList();
        abbreviations = abbreviations == null ? Map.of()
                : abbreviations.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                        e -> e.getKey().trim().toUpperCase(java.util.Locale.ROOT),
                        e -> e.getValue().trim().toUpperCase(java.util.Locale.ROOT)));
    }

    /** The same values as application.yml, for tests and for callers of the pure function outside Spring. */
    public static AccountMappingProperties defaults() {
        return new AccountMappingProperties(new BigDecimal("0.5"),
                List.of("A", "O", "AS", "OS", "DE", "DA", "DO", "DAS", "DOS", "E", "C", "COM", "EM", "NO", "NA", "NOS",
                        "NAS", "P", "PARA", "POR"),
                Map.ofEntries(Map.entry("DESP", "DESPESA"), Map.entry("DESPS", "DESPESAS"),
                        Map.entry("MAT", "MATERIAL"), Map.entry("MANUT", "MANUTENCAO"), Map.entry("SERV", "SERVICO"),
                        Map.entry("SERVS", "SERVICOS"), Map.entry("REEMB", "REEMBOLSO"), Map.entry("ADM",
                                "ADMINISTRACAO"),
                        Map.entry("BANC", "BANCARIA"), Map.entry("TRANSP", "TRANSPORTE"), Map.entry("SIST", "SISTEMA"),
                                Map.entry("EQUIP", "EQUIPAMENTO")));
    }
}
