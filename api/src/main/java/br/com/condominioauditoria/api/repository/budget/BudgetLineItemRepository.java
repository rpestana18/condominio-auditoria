package br.com.condominioauditoria.api.repository.budget;

import br.com.condominioauditoria.api.model.budget.BudgetLineItem;
import br.com.condominioauditoria.api.model.enums.BudgetItemStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetLineItemRepository extends JpaRepository<BudgetLineItem, UUID> {

    List<BudgetLineItem> findByBudgetId(UUID budgetId);

    List<BudgetLineItem> findByCondominiumIdAndStatus(UUID condominiumId, BudgetItemStatus status);
}
