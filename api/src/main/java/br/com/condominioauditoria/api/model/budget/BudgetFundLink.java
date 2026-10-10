package br.com.condominioauditoria.api.model.budget;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/** Budget fund line (1.9.x) linked by the Admin to a fund of the cash flow (ADR 0004, Decision 7). */
@Entity
public class BudgetFundLink {

    @Id
    private UUID id;
    private UUID budgetId;
    private UUID budgetLineId;
    private UUID fundId;

    protected BudgetFundLink() {
    }

    public BudgetFundLink(UUID budgetId, UUID budgetLineId, UUID fundId) {
        this.id = UUID.randomUUID();
        this.budgetId = budgetId;
        this.budgetLineId = budgetLineId;
        this.fundId = fundId;
    }

    public UUID getBudgetId() {
        return budgetId;
    }

    public UUID getBudgetLineId() {
        return budgetLineId;
    }

    public UUID getFundId() {
        return fundId;
    }
}
