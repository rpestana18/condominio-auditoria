package br.com.condominioauditoria.api.model.accounting;

import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.LedgerEntryData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Ledger entry extracted, normalized and enriched. Points to the source file and page. */
@Entity
@Table(name = "lancamento")
public class LedgerEntry {

    @Id
    private UUID id;
    @Column(name = "condominio_id")
    private UUID condominiumId;
    @Column(name = "arquivo_id")
    private UUID fileId;
    @Column(name = "fundo_id")
    private UUID fundId;
    @Column(name = "data")
    private LocalDate date;
    @Column(name = "conta_codigo")
    private String accountCode;
    @Column(name = "conta_nome")
    private String accountName;
    @Column(name = "documento")
    private String document;
    @Column(name = "historico")
    private String memo;
    @Column(name = "credito")
    private BigDecimal credit;
    @Column(name = "debito")
    private BigDecimal debit;
    @Column(name = "saldo")
    private BigDecimal balance;
    @Column(name = "pagina")
    private int page;
    @Column(name = "ordem")
    private int sequence;
    @Column(name = "nota_fiscal")
    private String invoiceNumber;
    @Column(name = "fornecedor")
    private String supplier;
    @Column(name = "meio_pagamento")
    private String paymentMethod;
    @Column(name = "transferencia_entre_fundos")
    private boolean interFundTransfer;
    /** Credit from a condo fee receipt (v2). Null: saved before v2, the collection asks for a reprocess. */
    @Column(name = "recebimento_cota")
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
