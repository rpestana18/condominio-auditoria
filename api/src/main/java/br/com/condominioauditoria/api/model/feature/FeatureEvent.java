package br.com.condominioauditoria.api.model.feature;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * One enable or disable in the activation trail (RF-10.6). Insert only: the entity is immutable and the database
 * rejects update and delete (trigger of migration V11).
 */
@Entity
@Immutable
@Table(name = "evento_modulo")
public class FeatureEvent {

    @Id
    private UUID id;
    @Column(name = "condominio_id")
    private UUID condominiumId;
    @Column(name = "modulo")
    private String feature;
    @Column(name = "ligado_antes")
    private boolean enabledBefore;
    @Column(name = "ligado_depois")
    private boolean enabledAfter;
    @Column(name = "usuario")
    private String username;
    @Column(name = "quando")
    private Instant occurredAt;
    /** Optional (RF-10.6): null when not given. */
    @Column(name = "motivo")
    private String reason;

    protected FeatureEvent() {
    }

    public FeatureEvent(UUID condominiumId, String feature, boolean enabledBefore, boolean enabledAfter,
            String username, Instant at, String reason) {
        this.id = UUID.randomUUID();
        this.condominiumId = condominiumId;
        this.feature = feature;
        this.enabledBefore = enabledBefore;
        this.enabledAfter = enabledAfter;
        this.username = username;
        this.occurredAt = at;
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public String getFeature() {
        return feature;
    }

    public boolean isEnabledBefore() {
        return enabledBefore;
    }

    public boolean isEnabledAfter() {
        return enabledAfter;
    }

    public String getUsername() {
        return username;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getReason() {
        return reason;
    }
}
