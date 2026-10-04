package br.com.condominioauditoria.rag.leitura.fluxo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class EnriquecedorTest {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    @Test
    void creditoRecibosAcumuladosEhRecebimentoDeCota() {
        assertThat(Enriquecedor.enriquecer("", "RECIBOS ACUMULADOS", new BigDecimal("321.31"), ZERO).recebimentoCota())
                .isTrue();
    }

    @Test
    void estornoNaColunaDeCreditoTambemEhMarcado() {
        assertThat(Enriquecedor.recebimentoCota("RECIBOS ACUMULADOS", new BigDecimal("-260.56"), ZERO)).isTrue();
    }

    @Test
    void debitoNuncaEhRecebimentoDeCota() {
        assertThat(Enriquecedor.recebimentoCota("RECIBOS ACUMULADOS", ZERO, new BigDecimal("10.00"))).isFalse();
        assertThat(Enriquecedor.recebimentoCota("RECIBOS ACUMULADOS", ZERO, ZERO)).isFalse();
    }

    @Test
    void outrosCreditosNaoSaoCota() {
        assertThat(Enriquecedor.recebimentoCota("RECIBOS DE CONTRATO", new BigDecimal("1742.10"), ZERO)).isFalse();
        assertThat(Enriquecedor.recebimentoCota("TRANSFERENCIA DE CONDOMÍNIO", new BigDecimal("50.00"), ZERO)).isFalse();
    }
}
