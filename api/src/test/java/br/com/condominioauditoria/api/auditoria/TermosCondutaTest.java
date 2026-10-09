package br.com.condominioauditoria.api.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** RF-04.15 (Q11): termos de conduta achados sem acento, em maiúsculas e no plural; palavras parecidas não. */
class TermosCondutaTest {

    @Test
    void achaOsTermosDaLista() {
        assertThat(TermosConduta.encontrados("Possível FRAUDE e desvios; roubo? A culpa")).containsExactly("fraude",
                "desvios", "roubo", "culpa");
    }

    @Test
    void naoConfundePalavrasParecidas() {
        assertThat(TermosConduta.encontrados("conta desvinculada; excesso de 8,6% do previsto; culpabilidade"))
                .isEmpty();
    }
}
