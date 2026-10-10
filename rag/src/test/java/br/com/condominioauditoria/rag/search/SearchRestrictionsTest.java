package br.com.condominioauditoria.rag.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Phrases and exclusions of the question, which also apply to the vector candidates of the hybrid search. */
class SearchRestrictionsTest {

    @Test
    void noPhraseNorExclusionDoesNotRestrict() {
        assertThat(SearchRestrictions.extract("multa por atraso")).isNull();
        assertThat(SearchRestrictions.extract("reajuste IGP-M")).isNull(); // hífen no meio da palavra não é exclusão
        assertThat(SearchRestrictions.extract("  ")).isNull();
        assertThat(SearchRestrictions.extract("aspas \"sem fechar")).isNull();
    }

    @Test
    void exclusion() {
        assertThat(SearchRestrictions.extract("salário -transporte")).isEqualTo("-transporte");
        assertThat(SearchRestrictions.extract("-transporte salário -vale")).isEqualTo("-transporte -vale");
    }

    @Test
    void requiredPhraseAndExcludedPhrase() {
        assertThat(SearchRestrictions.extract("\"fundo de reserva\" taxa")).isEqualTo("\"fundo de reserva\"");
        assertThat(SearchRestrictions.extract("salário -\"vale transporte\" -cesta"))
                .isEqualTo("-\"vale transporte\" -cesta");
        assertThat(SearchRestrictions.extract("\"\" multa")).isNull();
    }
}
