package br.com.condominioauditoria.rag.indice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Frases e exclusões da pergunta, que também valem para os candidatos vetoriais da busca híbrida. */
class RestricoesBuscaTest {

    @Test
    void semFraseNemExclusaoNaoRestringe() {
        assertThat(RestricoesBusca.extrair("multa por atraso")).isNull();
        assertThat(RestricoesBusca.extrair("reajuste IGP-M")).isNull(); // hífen no meio da palavra não é exclusão
        assertThat(RestricoesBusca.extrair("  ")).isNull();
        assertThat(RestricoesBusca.extrair("aspas \"sem fechar")).isNull();
    }

    @Test
    void exclusao() {
        assertThat(RestricoesBusca.extrair("salário -transporte")).isEqualTo("-transporte");
        assertThat(RestricoesBusca.extrair("-transporte salário -vale")).isEqualTo("-transporte -vale");
    }

    @Test
    void fraseObrigatoriaEFraseExcluida() {
        assertThat(RestricoesBusca.extrair("\"fundo de reserva\" taxa")).isEqualTo("\"fundo de reserva\"");
        assertThat(RestricoesBusca.extrair("salário -\"vale transporte\" -cesta"))
                .isEqualTo("-\"vale transporte\" -cesta");
        assertThat(RestricoesBusca.extrair("\"\" multa")).isNull();
    }
}
