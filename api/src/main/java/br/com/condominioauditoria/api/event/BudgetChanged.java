package br.com.condominioauditoria.api.event;

import br.com.condominioauditoria.api.model.audit.RecalculationTrigger;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Change that affects a condominium's budget vs. actual: cash flow saved, budget confirmed, account mapping changed,
 * funds linked, reallocation made or undone (ADR 0004, Decision 5). Published inside the change's transaction; findings
 * are recalculated after the commit.
 */
public record BudgetChanged(UUID condominiumId, RecalculationTrigger trigger) {

    public BudgetChanged {
        Objects.requireNonNull(condominiumId, "condominioId");
        Objects.requireNonNull(trigger, "gatilho");
    }

    public static BudgetChanged of(UUID condominiumId, String description, String username, Instant at) {
        return new BudgetChanged(condominiumId, new RecalculationTrigger(description, username, at));
    }
}
