package br.com.condominioauditoria.rag.parser.cashflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

public class EntryEnricherTest {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    @Test
    public void accumulatedReceiptsCreditIsCondoFeeReceipt() {
        assertThat(EntryEnricher.enrich("", "RECIBOS ACUMULADOS", new BigDecimal("321.31"), ZERO).condoFeeReceipt())
                .isTrue();
    }

    @Test
    public void reversalInCreditColumnIsAlsoMarked() {
        assertThat(EntryEnricher.condoFeeReceipt("RECIBOS ACUMULADOS", new BigDecimal("-260.56"), ZERO)).isTrue();
    }

    @Test
    public void debitIsNeverCondoFeeReceipt() {
        assertThat(EntryEnricher.condoFeeReceipt("RECIBOS ACUMULADOS", ZERO, new BigDecimal("10.00"))).isFalse();
        assertThat(EntryEnricher.condoFeeReceipt("RECIBOS ACUMULADOS", ZERO, ZERO)).isFalse();
    }

    @Test
    public void otherCreditsAreNotCondoFee() {
        assertThat(EntryEnricher.condoFeeReceipt("RECIBOS DE CONTRATO", new BigDecimal("1742.10"), ZERO)).isFalse();
        assertThat(EntryEnricher.condoFeeReceipt("TRANSFERENCIA DE CONDOMÍNIO", new BigDecimal("50.00"),
                ZERO)).isFalse();
    }
}
