package br.com.condominioauditoria.api.model.budget;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/** Budget audit trail: who, when, what. Insert only (the database rejects update and delete). */
@Entity
public class BudgetEvent {

    public static final String CONFIRMED = "CONFIRMADA";
    public static final String SUPERSEDED = "SUBSTITUIDA";
    /** Link of the 1.9.x lines to the funds changed after confirmation (RF-03.1.9). */
    public static final String FUNDS_CHANGED = "FUNDOS_ALTERADOS";
    /** Budget marked as extended by the Admin, with a justification (RF-11.3). */
    public static final String EXTENDED = "PRORROGADA";
    /** Extension undone by the Admin. */
    public static final String EXTENSION_UNDONE = "PRORROGACAO_DESFEITA";
    /**
     * Extension shortened (or undone) automatically because another budget was confirmed in the extended months
     * (RF-11.3).
     */
    public static final String EXTENSION_SHORTENED = "PRORROGACAO_ENCURTADA";

    @Id
    private UUID id;
    private UUID budgetId;
    private UUID condominiumId;
    private String type;
    private String username;
    private Instant occurredAt;
    private String justification;
    private String detail;

    protected BudgetEvent() {
    }

    public BudgetEvent(Budget budget, String type, String username, Instant at, String justification,
            String detail) {
        this.id = UUID.randomUUID();
        this.budgetId = budget.getId();
        this.condominiumId = budget.getCondominiumId();
        this.type = type;
        this.username = username;
        this.occurredAt = at;
        this.justification = justification;
        this.detail = detail;
    }

    public UUID getBudgetId() {
        return budgetId;
    }

    public String getType() {
        return type;
    }

    public String getUsername() {
        return username;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getJustification() {
        return justification;
    }

    public String getDetail() {
        return detail;
    }
}
