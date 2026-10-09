package br.com.condominioauditoria.api.repository.condominium;

import br.com.condominioauditoria.api.model.condominium.Condominium;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CondominiumRepository extends JpaRepository<Condominium, UUID> {

    /**
     * select ... for update: serializes operations that need to look at the whole condominium (e.g. one budget per
     * month).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Condominium c where c.id = :id")
    Optional<Condominium> lockById(UUID id);
}
