package br.com.condominioauditoria.api.model.accounting;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Fingerprint of a ledger entry: what identifies an entry in the cash flow regardless of its {@code id}, which changes
 * on every reprocess (saving deletes and recreates). File, page, sequence, date, cash flow account, document and amount
 * (ADR 0004, Decision 3). Same read of the same file = same key; if the cash flow changed, the key does not match and
 * whoever uses it warns. Pure function.
 */
public record LedgerEntryFingerprint(UUID fileId, int page, int sequence, LocalDate date, String account,
        String document, BigDecimal debit, BigDecimal credit) {

    public LedgerEntryFingerprint {
        Objects.requireNonNull(fileId, "arquivoId");
        Objects.requireNonNull(date, "data");
        Objects.requireNonNull(debit, "debito");
        Objects.requireNonNull(credit, "credito");
    }

    public static LedgerEntryFingerprint of(LedgerEntry l) {
        return new LedgerEntryFingerprint(l.getFileId(), l.getPage(), l.getSequence(), l.getDate(), l.getAccountCode(),
                l.getDocument(), l.getDebit(), l.getCredit());
    }

    public static String key(LedgerEntry l) {
        return of(l).key();
    }

    /** SHA-256 (hex, 64 characters) of the canonical text; money with 2 decimals, empty instead of null. */
    public String key() {
        String canonical = String.join("|", fileId.toString(), Integer.toString(page), Integer.toString(sequence),
                date.toString(), text(account), text(document), money(debit), money(credit));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(String s) {
        return s == null ? "" : s.trim();
    }

    private static String money(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString();
    }
}
