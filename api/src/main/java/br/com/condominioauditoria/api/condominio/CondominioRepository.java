package br.com.condominioauditoria.api.condominio;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CondominioRepository extends JpaRepository<Condominio, UUID> {

    /** select ... for update: serializa operações que precisam olhar o condomínio inteiro (ex.: uma PO por mês). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Condominio c where c.id = :id")
    Optional<Condominio> travar(UUID id);
}
