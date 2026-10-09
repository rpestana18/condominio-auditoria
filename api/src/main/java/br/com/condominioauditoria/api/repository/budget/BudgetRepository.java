package br.com.condominioauditoria.api.repository.budget;

import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    Optional<Budget> findByFileId(UUID fileId);

    Optional<Budget> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    List<Budget> findByCondominiumIdOrderByReadAtDesc(UUID condominiumId);

    List<Budget> findByCondominiumIdAndStatusIn(UUID condominiumId, Collection<BudgetStatus> statuses);
}
