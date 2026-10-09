package br.com.condominioauditoria.api.repository.budget;

import br.com.condominioauditoria.api.model.budget.Reallocation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reallocations of a budget version (RF-03.1.7). */
public interface ReallocationRepository extends JpaRepository<Reallocation, UUID> {

    List<Reallocation> findByBudgetIdOrderByDateAscReallocatedAtAsc(UUID budgetId);

    List<Reallocation> findByBudgetIdAndUndoneAtIsNull(UUID budgetId);

    Optional<Reallocation> findByBudgetIdAndEntryKeyAndUndoneAtIsNull(UUID budgetId,
            String entryKey);

    Optional<Reallocation> findByIdAndCondominiumId(UUID id, UUID condominiumId);
}
