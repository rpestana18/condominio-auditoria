package br.com.condominioauditoria.api.model.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.Enrichment;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.LedgerEntryData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR 0004, Decision 3: the entry key survives a reprocess (new id) and changes when the cash flow changes. */
class LedgerEntryFingerprintTest {

    private final UUID condominium = UUID.randomUUID();
    private final UUID file = UUID.randomUUID();
    private final UUID fund = UUID.randomUUID();

    @Test
    void sameReadOfTheSameFileHasTheSameKeyWithNewId() {
        LedgerEntry first = new LedgerEntry(condominium, file, fund, read("1064", "250.00", 12));
        LedgerEntry reprocessed = new LedgerEntry(condominium, file, fund, read("1064", "250.00", 12));

        assertThat(reprocessed.getId()).isNotEqualTo(first.getId());
        assertThat(LedgerEntryFingerprint.key(reprocessed)).isEqualTo(LedgerEntryFingerprint.key(first)).hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void differentAmountSequenceAccountOrFileChangeTheKey() {
        String base = LedgerEntryFingerprint.key(new LedgerEntry(condominium, file, fund, read("1064", "250.00", 12)));

        assertThat(LedgerEntryFingerprint.key(new LedgerEntry(condominium, file, fund, read("1064", "250.01", 12))))
                .isNotEqualTo(base);
        assertThat(LedgerEntryFingerprint.key(new LedgerEntry(condominium, file, fund, read("1064", "250.00", 13))))
                .isNotEqualTo(base);
        assertThat(LedgerEntryFingerprint.key(new LedgerEntry(condominium, file, fund, read("1065", "250.00", 12))))
                .isNotEqualTo(base);
        assertThat(LedgerEntryFingerprint.key(new LedgerEntry(condominium, UUID.randomUUID(), fund,
                read("1064", "250.00", 12)))).isNotEqualTo(base);
    }

    @Test
    void amountScaleDoesNotChangeTheKey() {
        var a = new LedgerEntryFingerprint(file, 3, 12, LocalDate.of(2026, 9, 9), "1064", null, new BigDecimal("250.0"),
                BigDecimal.ZERO);
        var b = new LedgerEntryFingerprint(file, 3, 12, LocalDate.of(2026, 9, 9), "1064", "", new BigDecimal("250.00"),
                new BigDecimal("0.00"));

        assertThat(a.key()).isEqualTo(b.key());
    }

    private static LedgerEntryData read(String account, String amount, int sequence) {
        return new LedgerEntryData(3, sequence, LocalDate.of(2026, 9, 9), account, "CARTAO", "", "Compra no cartão",
                BigDecimal.ZERO.setScale(2), new BigDecimal(amount), BigDecimal.ZERO.setScale(2),
                new Enrichment(null, null, "CARTAO", false, false));
    }
}
