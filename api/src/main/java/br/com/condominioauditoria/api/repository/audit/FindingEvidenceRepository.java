package br.com.condominioauditoria.api.repository.audit;

import br.com.condominioauditoria.api.model.audit.FindingEvidence;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FindingEvidenceRepository extends JpaRepository<FindingEvidence, UUID> {

    List<FindingEvidence> findByFindingIdInOrderByPositionAsc(Collection<UUID> findings);
}
