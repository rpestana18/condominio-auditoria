package br.com.condominioauditoria.api.repository.budget;

import br.com.condominioauditoria.api.model.budget.ReallocationEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reallocation trail (insert-only). */
public interface ReallocationEventRepository extends JpaRepository<ReallocationEvent, UUID> {
}
