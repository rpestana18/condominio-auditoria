package br.com.condominioauditoria.api.repository.feature;

import br.com.condominioauditoria.api.model.feature.FeatureEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureEventRepository extends JpaRepository<FeatureEvent, UUID> {

    /** Oldest first; the id breaks ties between events at the same instant (stable order). */
    List<FeatureEvent> findByCondominiumIdAndFeatureOrderByOccurredAtAscIdAsc(UUID condominiumId, String feature);
}
