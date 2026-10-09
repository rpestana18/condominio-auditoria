package br.com.condominioauditoria.api.repository.budget;

import br.com.condominioauditoria.api.model.budget.BudgetFundLink;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BudgetFundLinkRepository extends JpaRepository<BudgetFundLink, UUID> {
    List<BudgetFundLink> findByBudgetId(UUID budgetId);

    /**
     * Deletes the current link (the state; the trail stays in evento_previsao) in the database right away, before the
     * new inserts: the unique keys per line and per fund cannot collide in the middle of the change.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from BudgetFundLink f where f.budgetId = :budgetId")
    void deleteByBudgetId(UUID budgetId);
}
