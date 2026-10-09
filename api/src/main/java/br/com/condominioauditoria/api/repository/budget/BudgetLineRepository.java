package br.com.condominioauditoria.api.repository.budget;

import br.com.condominioauditoria.api.model.budget.BudgetLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BudgetLineRepository extends JpaRepository<BudgetLine, UUID> {

    List<BudgetLine> findByBudgetIdOrderByPosition(UUID budgetId);

    @Modifying
    @Query("delete from BudgetLine l where l.budgetId = :budgetId")
    void deleteByBudgetId(UUID budgetId);
}
