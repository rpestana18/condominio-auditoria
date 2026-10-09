package br.com.condominioauditoria.api.repository.audit;

import br.com.condominioauditoria.api.model.audit.Finding;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FindingRepository extends JpaRepository<Finding, UUID> {

    Optional<Finding> findByCondominiumIdAndRuleAndReferenceMonthAndTarget(UUID condominiumId, String rule,
            LocalDate referenceMonth, String target);

    List<Finding> findByCondominiumIdAndTargetStartingWithOrderByCreatedAt(UUID condominiumId, String targetPrefix);

    /** Findings of the rules recalculated in a month (to close the ones that no longer apply). */
    List<Finding> findByCondominiumIdAndReferenceMonthAndRuleIn(UUID condominiumId, LocalDate referenceMonth,
            Collection<String> rules);

    List<Finding> findByCondominiumIdAndReferenceMonthOrderByCreatedAt(UUID condominiumId, LocalDate referenceMonth);

    List<Finding> findByCondominiumIdOrderByReferenceMonthDescCreatedAtAsc(UUID condominiumId);
}
