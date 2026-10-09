package br.com.condominioauditoria.rag.parser.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

public class BudgetAmountTest {

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "1585,14;1585.14",
            "1.585,14;1585.14",
            "474.201,13;474201.13",
            "106.196,03;106196.03",
            "1000,00;1000.00",
            "0,00;0.00",
            "-941,54;-941.54"})
    public void withAndWithoutThousandsSeparator(String text, String expected) {
        assertThat(BudgetAmount.fromText(text)).isEqualByComparingTo(expected).hasScaleOf(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"7,45%", "1.58,14", "12.34,56", "1585,1", "R$4.100,00", "2.650", "-", "1.9.1"})
    public void isNotAmount(String text) {
        assertThat(BudgetAmount.isAmount(text)).isFalse();
        assertThatThrownBy(() -> BudgetAmount.fromText(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void scaleIsAlwaysTwoDecimals() {
        assertThat(BudgetAmount.fromText("1189,16").scale()).isEqualTo(2);
    }
}
