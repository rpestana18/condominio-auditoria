package br.com.condominioauditoria.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DinheiroTest {

    @Test
    void converteFormatoBrasileiro() {
        assertThat(Dinheiro.deTextoBr("1.234.567,89")).isEqualByComparingTo("1234567.89");
        assertThat(Dinheiro.deTextoBr("-941,54")).isEqualByComparingTo("-941.54");
        assertThat(Dinheiro.deTextoBr("0,00").scale()).isEqualTo(2);
    }

    @Test
    void tracoSignificaZero() {
        assertThat(Dinheiro.deTextoBr("-")).isEqualByComparingTo("0");
    }

    @Test
    void recusaTextoQueNaoEValor() {
        assertThat(Dinheiro.ehValorBr("02/02,")).isFalse();
        assertThat(Dinheiro.ehValorBr("1234,5")).isFalse();
        assertThatThrownBy(() -> Dinheiro.deTextoBr("NF: 923152")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formataParaLeitura() {
        assertThat(Dinheiro.formatarBr(new java.math.BigDecimal("457051.86"))).isEqualTo("457.051,86");
    }
}
