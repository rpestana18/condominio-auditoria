package br.com.condominioauditoria.api.model.condominium;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

@Entity
public class Condominium {

    @Id
    private UUID id;
    private String name;
    private String cnpj;
    private Instant createdAt;
    private UUID operatingFundId;
    private String operatingFundConfirmedBy;
    private Instant operatingFundConfirmedAt;

    protected Condominium() {
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCnpj() {
        return cnpj;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getOperatingFundId() {
        return operatingFundId;
    }

    public String getOperatingFundConfirmedBy() {
        return operatingFundConfirmedBy;
    }

    public Instant getOperatingFundConfirmedAt() {
        return operatingFundConfirmedAt;
    }

    /** The Manager or the Admin confirms which fund is the operating fund (RF-05.1b). */
    public void confirmOperatingFund(UUID fundId, String username, Instant at) {
        this.operatingFundId = fundId;
        this.operatingFundConfirmedBy = username;
        this.operatingFundConfirmedAt = at;
    }
}
