package br.com.condominioauditoria.api.repository.budget;

import br.com.condominioauditoria.api.model.budget.AccountMappingEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountMappingEventRepository extends JpaRepository<AccountMappingEvent, UUID> {
    List<AccountMappingEvent> findByBudgetIdOrderByOccurredAtAscAccountCodeAsc(UUID budgetId);
}
