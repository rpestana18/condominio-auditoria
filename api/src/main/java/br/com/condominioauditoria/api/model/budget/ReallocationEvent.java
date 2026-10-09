package br.com.condominioauditoria.api.model.budget;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Reallocation trail (insert-only: the database rejects update and delete). */
@Entity
@Table(name = "evento_realocacao")
public class ReallocationEvent {

    public static final String REALOCADA = "REALOCADA";
    public static final String DESFEITA = "DESFEITA";

    @Id
    private UUID id;
    @Column(name = "realocacao_id")
    private UUID reallocationId;
    @Column(name = "condominio_id")
    private UUID condominiumId;
    @Column(name = "acao")
    private String action;
    @Column(name = "usuario")
    private String username;
    @Column(name = "em")
    private Instant occurredAt;
    @Column(name = "detalhe")
    private String detail;

    protected ReallocationEvent() {
    }

    public ReallocationEvent(Reallocation r, String action, String username, Instant at, String detail) {
        this.id = UUID.randomUUID();
        this.reallocationId = r.getId();
        this.condominiumId = r.getCondominiumId();
        this.action = action;
        this.username = username;
        this.occurredAt = at;
        this.detail = detail;
    }

    public UUID getReallocationId() {
        return reallocationId;
    }

    public String getAction() {
        return action;
    }

    public String getUsername() {
        return username;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getDetail() {
        return detail;
    }
}
