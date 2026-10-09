package br.com.condominioauditoria.api.repository.budget;

import br.com.condominioauditoria.api.model.budget.BudgetEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetEventRepository extends JpaRepository<BudgetEvent, UUID> {
    List<BudgetEvent> findByBudgetIdOrderByOccurredAt(UUID budgetId);
}
