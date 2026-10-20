package br.com.condominioauditoria.api.repository.budget;

import br.com.condominioauditoria.api.model.budget.SpendingProfileEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpendingProfileEventRepository extends JpaRepository<SpendingProfileEvent, UUID> {
    List<SpendingProfileEvent> findByBudgetIdOrderByOccurredAtAscLineCodeAsc(UUID budgetId);
}
