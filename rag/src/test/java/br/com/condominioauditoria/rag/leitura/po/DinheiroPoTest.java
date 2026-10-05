package br.com.condominioauditoria.rag.leitura.po;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DinheiroPoTest {

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "1585,14;1585.14",
            "1.585,14;1585.14",
            "474.201,13;474201.13",
            "106.196,03;106196.03",
            "1000,00;1000.00",
            "0,00;0.00",
            "-941,54;-941.54"})
    void comESemSeparadorDeMilhar(String texto, String esperado) {
        assertThat(DinheiroPo.deTexto(texto)).isEqualByComparingTo(esperado).hasScaleOf(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"7,45%", "1.58,14", "12.34,56", "1585,1", "R$4.100,00", "2.650", "-", "1.9.1"})
    void naoEhValor(String texto) {
        assertThat(DinheiroPo.ehValor(texto)).isFalse();
        assertThatThrownBy(() -> DinheiroPo.deTexto(texto)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void escalaSempreDuasCasas() {
        assertThat(DinheiroPo.deTexto("1189,16").scale()).isEqualTo(2);
    }
}
