package br.com.condominioauditoria.api.repository.budget;

import br.com.condominioauditoria.api.model.budget.BudgetLineOrigin;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetLineOriginRepository extends JpaRepository<BudgetLineOrigin, UUID> {

    List<BudgetLineOrigin> findByBudgetId(UUID budgetId);

    Optional<BudgetLineOrigin> findByBudgetLineId(UUID budgetLineId);
}
