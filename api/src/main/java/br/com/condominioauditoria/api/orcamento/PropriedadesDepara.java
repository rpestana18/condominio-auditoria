package br.com.condominioauditoria.api.orcamento;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parâmetros da sugestão do de-para pelo nome (bloco "condominio.orcamento.depara" do application.yml; ADR 0004,
 * Decisão 4). A comparação é de texto, sem IA.
 *
 * @param notaMinima menor proporção de palavras em comum (0 a 1) para virar sugestão
 * @param palavrasVazias palavras que não contam na comparação (já sem acento, em maiúsculas)
 * @param abreviacoes abreviação → palavra inteira (já sem acento, em maiúsculas), ex.: DESP → DESPESA
 */
@ConfigurationProperties(prefix = "condominio.orcamento.depara")
public record PropriedadesDepara(BigDecimal notaMinima, List<String> palavrasVazias, Map<String, String> abreviacoes) {

    public PropriedadesDepara {
        if (notaMinima == null || notaMinima.signum() <= 0 || notaMinima.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("condominio.orcamento.depara.nota-minima deve estar entre 0 (exclusive) e 1");
        }
        // A configuração pode vir em minúsculas (chaves de mapa no YAML): a comparação é sempre em maiúsculas
        palavrasVazias = palavrasVazias == null ? List.of()
                : palavrasVazias.stream().map(p -> p.trim().toUpperCase(java.util.Locale.ROOT)).toList();
        abreviacoes = abreviacoes == null ? Map.of()
                : abreviacoes.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                        e -> e.getKey().trim().toUpperCase(java.util.Locale.ROOT),
                        e -> e.getValue().trim().toUpperCase(java.util.Locale.ROOT)));
    }

    /** Os mesmos valores do application.yml, para testes e para quem usa a função pura fora do Spring. */
    public static PropriedadesDepara padrao() {
        return new PropriedadesDepara(new BigDecimal("0.5"),
                List.of("A", "O", "AS", "OS", "DE", "DA", "DO", "DAS", "DOS", "E", "C", "COM", "EM", "NO", "NA", "NOS",
                        "NAS", "P", "PARA", "POR"),
                Map.ofEntries(Map.entry("DESP", "DESPESA"), Map.entry("DESPS", "DESPESAS"),
                        Map.entry("MAT", "MATERIAL"), Map.entry("MANUT", "MANUTENCAO"), Map.entry("SERV", "SERVICO"),
                        Map.entry("SERVS", "SERVICOS"), Map.entry("REEMB", "REEMBOLSO"), Map.entry("ADM", "ADMINISTRACAO"),
                        Map.entry("BANC", "BANCARIA"), Map.entry("TRANSP", "TRANSPORTE"), Map.entry("SIST", "SISTEMA"), Map.entry("EQUIP", "EQUIPAMENTO")));
    }
}
