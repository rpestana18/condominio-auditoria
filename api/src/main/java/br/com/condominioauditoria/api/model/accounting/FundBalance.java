package br.com.condominioauditoria.api.model.accounting;

import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.FundPosition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Position of a fund in the period of a report (line of the Posição Financeira). */
@Entity
@Table(name = "saldo_fundo")
public class FundBalance {

    @Id
    private UUID id;
    @Column(name = "condominio_id")
    private UUID condominiumId;
    @Column(name = "arquivo_id")
    private UUID fileId;
    @Column(name = "fundo_id")
    private UUID fundId;
    @Column(name = "periodo_inicio")
    private LocalDate periodStart;
    @Column(name = "periodo_fim")
    private LocalDate periodEnd;
    @Column(name = "saldo_anterior")
    private BigDecimal openingBalance;
    @Column(name = "creditos")
    private BigDecimal credits;
    @Column(name = "debitos")
    private BigDecimal debits;
    @Column(name = "saldo_atual")
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
