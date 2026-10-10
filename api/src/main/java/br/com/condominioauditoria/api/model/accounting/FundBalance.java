package br.com.condominioauditoria.api.model.accounting;

import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.FundPosition;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Position of a fund in the period of a report (line of the Posição Financeira). */
@Entity
public class FundBalance {

    @Id
    private UUID id;
    private UUID condominiumId;
    private UUID fileId;
    private UUID fundId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal openingBalance;
    private BigDecimal credits;
    private BigDecimal debits;
    private BigDecimal closingBalance;

    protected FundBalance() {
    }

    public FundBalance(UUID condominiumId, UUID fileId, UUID fundId, LocalDate start, LocalDate end, FundPosition p) {
        this.id = UUID.randomUUID();
        this.condominiumId = condominiumId;
        this.fileId = fileId;
        this.fundId = fundId;
        this.periodStart = start;
        this.periodEnd = end;
        this.openingBalance = p.openingBalance();
        this.credits = p.credits();
        this.debits = p.debits();
        this.closingBalance = p.closingBalance();
    }

    public UUID getFundId() {
        return fundId;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public BigDecimal getCredits() {
        return credits;
    }

    public BigDecimal getDebits() {
        return debits;
    }

    public BigDecimal getClosingBalance() {
        return closingBalance;
    }
}
