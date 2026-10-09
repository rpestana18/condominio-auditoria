package br.com.condominioauditoria.api.service.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** RF-04.15 (Q11): conduct terms found without accents, in uppercase and in the plural; similar words are not. */
class ConductTermsTest {

    @Test
    void findsTheListedTerms() {
        assertThat(ConductTerms.find("Possível FRAUDE e desvios; roubo? A culpa")).containsExactly("fraude",
                "desvios", "roubo", "culpa");
    }

    @Test
    void doesNotConfuseSimilarWords() {
        assertThat(ConductTerms.find("conta desvinculada; excesso de 8,6% do previsto; culpabilidade"))
                .isEmpty();
    }
}
