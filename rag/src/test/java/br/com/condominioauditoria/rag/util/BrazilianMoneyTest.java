package br.com.condominioauditoria.rag.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class BrazilianMoneyTest {

    @Test
    public void parsesBrazilianFormat() {
        assertThat(BrazilianMoney.fromText("1.234.567,89")).isEqualByComparingTo("1234567.89");
        assertThat(BrazilianMoney.fromText("-941,54")).isEqualByComparingTo("-941.54");
        assertThat(BrazilianMoney.fromText("0,00").scale()).isEqualTo(2);
    }

    @Test
    public void dashMeansZero() {
        assertThat(BrazilianMoney.fromText("-")).isEqualByComparingTo("0");
    }

    @Test
    public void rejectsTextThatIsNotAnAmount() {
        assertThat(BrazilianMoney.isAmount("02/02,")).isFalse();
        assertThat(BrazilianMoney.isAmount("1234,5")).isFalse();
        assertThatThrownBy(() -> BrazilianMoney.fromText("NF: 923152")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void formatsForReading() {
        assertThat(BrazilianMoney.format(new java.math.BigDecimal("457051.86"))).isEqualTo("457.051,86");
    }
}
