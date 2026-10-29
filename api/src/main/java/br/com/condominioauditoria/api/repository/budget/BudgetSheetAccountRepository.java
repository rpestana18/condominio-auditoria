package br.com.condominioauditoria.api.repository.budget;

import br.com.condominioauditoria.api.model.budget.BudgetSheetAccount;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BudgetSheetAccountRepository extends JpaRepository<BudgetSheetAccount, UUID> {

    List<BudgetSheetAccount> findByBudgetIdOrderBySheetRow(UUID budgetId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from BudgetSheetAccount a where a.budgetId = :budgetId")
    int deleteByBudgetId(UUID budgetId);
}
