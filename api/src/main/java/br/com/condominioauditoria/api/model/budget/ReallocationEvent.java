package br.com.condominioauditoria.api.model.budget;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/** Reallocation trail (insert-only: the database rejects update and delete). */
@Entity
public class ReallocationEvent {

    public static final String REALOCADA = "REALOCADA";
    public static final String DESFEITA = "DESFEITA";

    @Id
    private UUID id;
    private UUID reallocationId;
    private UUID condominiumId;
    private String action;
    private String username;
    private Instant occurredAt;
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
