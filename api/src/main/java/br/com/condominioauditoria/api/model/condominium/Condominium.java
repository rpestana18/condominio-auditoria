package br.com.condominioauditoria.api.model.condominium;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "condominio")
public class Condominium {

    @Id
    private UUID id;
    @Column(name = "nome")
    private String name;
    private String cnpj;
    @Column(name = "criado_em")
    private Instant createdAt;
    @Column(name = "fundo_ordinario_id")
    private UUID operatingFundId;
    @Column(name = "fundo_ordinario_confirmado_por")
    private String operatingFundConfirmedBy;
    @Column(name = "fundo_ordinario_confirmado_em")
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
