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
        assertThat(EntryEnricher.condoFeeReceipt("RECIBOS DE CONTRACT", new BigDecimal("1742.10"), ZERO)).isFalse();
        assertThat(EntryEnricher.condoFeeReceipt("TRANSFER DE CONDOMÍNIO", new BigDecimal("50.00"),
                ZERO)).isFalse();
    }

    /** D3: account names of the pilot for a transfer between funds, with and without accents, plural and abbreviated. */
    @Test
    public void transferAccountsWithAccentsPluralAndAbbreviation() {
        assertThat(EntryEnricher.interFundTransfer("TRANSFERENCIA CONTABIL", "QUALQUER")).isTrue();
        assertThat(EntryEnricher.interFundTransfer("TRANSFERÊNCIAS CONTÁBEIS", "QUALQUER")).isTrue();
        assertThat(EntryEnricher.interFundTransfer("TRANSF CONTABEIS", "QUALQUER")).isTrue();
        assertThat(EntryEnricher.interFundTransfer("TRANSF. COTA EXTRA DÉBITO", "QUALQUER")).isTrue();
        assertThat(EntryEnricher.interFundTransfer("TRANSF A DEBITOS OBRAS", "QUALQUER")).isTrue();
        assertThat(EntryEnricher.interFundTransfer("AJUSTE CONTÁBIL", "QUALQUER")).isTrue();
    }

    /** D3: both sides carry the same memo, so the memo marks the side with a generic account. */
    @Test
    public void transferMemoMarksBothSides() {
        assertThat(EntryEnricher.interFundTransfer("TRANSFERÊNCIA ENTRE CONTAS",
                "TRANSFERENCIA CONTABIL DE COND. PARA REPASSE LANCHES")).isTrue();
        assertThat(EntryEnricher.interFundTransfer("TRANSFERÊNCIA ENTRE CONTAS",
                "TRANSFERENCIA CONTABIL GRUPO OBRAS PARA OBRAS/REFORMAS/INFRA")).isTrue();
        assertThat(EntryEnricher.interFundTransfer("OUTROS DEBITOS CONCESSIONARIAS",
                "AJUSTE CONTÁBIL DE CONCESSIONÁRIAS PARA AGUA/ESGOTO REF. A RECUPERAÇÃO DE CRÉDITO")).isTrue();
        assertThat(EntryEnricher.interFundTransfer("TRANSFERÊNCIA ENTRE CONTAS",
                "AJUSTE CONTÁBIL DE CONCESSIONÁRIAS PARA ENERGIA ELETRICA")).isTrue();
        assertThat(EntryEnricher.interFundTransfer("", "TRANSFERÊNCIA DE CONDOMÍNIO")).isTrue();
        assertThat(EntryEnricher.enrich("TRANSFERÊNCIAS CONTÁBEIS", "TRANSFERENCIA CONTABIL DE OBRAS PARA CONDOMINIO",
                new BigDecimal("23.21"), ZERO).interFundTransfer()).isTrue();
    }

    /**
     * "TRANSFERÊNCIA ENTRE CONTAS" with money from an account outside the report is not a transfer between funds: the
     * other side is not in the report, and marking it would make the sum of transfers different from 0,00.
     */
    @Test
    public void moneyFromOutsideTheReportIsNotInterFund() {
        assertThat(EntryEnricher.interFundTransfer("TRANSFERÊNCIA ENTRE CONTAS",
                "APORTE DA CONTA EXTERNA PARA COBERTURA DE DESPESAS")).isFalse();
        assertThat(EntryEnricher.interFundTransfer("TRANSFERÊNCIA ENTRE CONTAS", "REPASSE PARCELA 1/2")).isFalse();
    }

    @Test
    public void ordinaryEntriesAreNotTransfers() {
        assertThat(EntryEnricher.interFundTransfer("PEÇAS E ACESSÓRIOS", "COMPRA DE TOLDO")).isFalse();
        assertThat(EntryEnricher.interFundTransfer("TARIFAS BANCARIAS", "TARIFA TRANSFERENCIA PIX")).isFalse();
        assertThat(EntryEnricher.interFundTransfer("", "RECIBOS ACUMULADOS")).isFalse();
        assertThat(EntryEnricher.interFundTransfer(null, "")).isFalse();
    }
}
