package br.com.condominioauditoria.api.repository.budget;

import br.com.condominioauditoria.api.model.budget.BudgetLineReviewEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetLineReviewEventRepository extends JpaRepository<BudgetLineReviewEvent, UUID> {
    List<BudgetLineReviewEvent> findByBudgetIdOrderByOccurredAtAscLineCodeAsc(UUID budgetId);
}
