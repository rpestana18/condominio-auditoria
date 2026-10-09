package br.com.condominioauditoria.api.repository.budget;

import br.com.condominioauditoria.api.model.budget.BudgetItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetItemRepository extends JpaRepository<BudgetItem, UUID> {

    List<BudgetItem> findByCondominiumIdOrderByName(UUID condominiumId);

    Optional<BudgetItem> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    boolean existsByCondominiumId(UUID condominiumId);
}
