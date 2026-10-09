package br.com.condominioauditoria.api.repository.budget;

import br.com.condominioauditoria.api.model.budget.AccountMapping;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountMappingRepository extends JpaRepository<AccountMapping, UUID> {

    List<AccountMapping> findByBudgetIdOrderByAccountCode(UUID budgetId);

    Optional<AccountMapping> findByBudgetIdAndAccountCode(UUID budgetId, String accountCode);
}
