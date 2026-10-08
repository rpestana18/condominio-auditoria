package br.com.condominioauditoria.api.auditoria;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoAchadoRepository extends JpaRepository<EventoAchado, UUID> {

    List<EventoAchado> findByAchadoIdInOrderByEmAsc(Collection<UUID> achados);
}
