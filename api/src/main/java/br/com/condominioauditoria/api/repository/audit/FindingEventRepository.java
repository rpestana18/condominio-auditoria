package br.com.condominioauditoria.api.repository.audit;

import br.com.condominioauditoria.api.model.audit.FindingEvent;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FindingEventRepository extends JpaRepository<FindingEvent, UUID> {

    List<FindingEvent> findByFindingIdInOrderByOccurredAtAsc(Collection<UUID> findings);
}
