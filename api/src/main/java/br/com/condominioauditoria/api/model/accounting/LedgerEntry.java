package br.com.condominioauditoria.api.model.accounting;

import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.LedgerEntryData;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Ledger entry extracted, normalized and enriched. Points to the source file and page. */
@Entity
public class LedgerEntry {

    @Id
    private UUID id;
    private UUID condominiumId;
    private UUID fileId;
    private UUID fundId;
    private LocalDate date;
    private String accountCode;
    private String accountName;
    private String document;
    private String memo;
    private BigDecimal credit;
    private BigDecimal debit;
    private BigDecimal balance;
    private int page;
    private int sequence;
    private String invoiceNumber;
    private String supplier;
    private String paymentMethod;
    private boolean interFundTransfer;
    /** Credit from a condo fee receipt (v2). Null: saved before v2, the collection asks for a reprocess. */
    private Boolean condoFeeReceipt;

    protected LedgerEntry() {
    }

    public LedgerEntry(UUID condominiumId, UUID fileId, UUID fundId, LedgerEntryData l) {
        this.id = UUID.randomUUID();
        this.condominiumId = condominiumId;
        this.fileId = fileId;
        this.fundId = fundId;
        this.date = l.date();
        this.accountCode = l.accountCode();
        this.accountName = l.accountName();
        this.document = l.document();
        this.memo = l.memo();
        this.credit = l.credit();
        this.debit = l.debit();
        this.balance = l.balance();
        this.page = l.page();
        this.sequence = l.sequence();
        this.invoiceNumber = l.enrichment().invoiceNumber();
        this.supplier = l.enrichment().supplier();
        this.paymentMethod = l.enrichment().paymentMethod();
        this.interFundTransfer = l.enrichment().interFundTransfer();
        this.condoFeeReceipt = l.enrichment().condoFeeReceipt();
    }

    public UUID getId() {
        return id;
    }

    public UUID getFileId() {
        return fileId;
    }

    public UUID getFundId() {
        return fundId;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getDocument() {
        return document;
    }

    public String getMemo() {
        return memo;
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public int getPage() {
        return page;
    }

    public int getSequence() {
        return sequence;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public String getSupplier() {
        return supplier;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public boolean isInterFundTransfer() {
        return interFundTransfer;
    }

    public Boolean getCondoFeeReceipt() {
        return condoFeeReceipt;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }
}
