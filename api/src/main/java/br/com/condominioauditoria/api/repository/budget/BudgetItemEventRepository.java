package br.com.condominioauditoria.api.repository.budget;

import br.com.condominioauditoria.api.model.budget.BudgetItemEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetItemEventRepository extends JpaRepository<BudgetItemEvent, UUID> {
    List<BudgetItemEvent> findByBudgetIdOrderByOccurredAtAscLineCodeAsc(UUID budgetId);
}
