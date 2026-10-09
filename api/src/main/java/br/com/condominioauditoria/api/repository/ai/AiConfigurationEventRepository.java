package br.com.condominioauditoria.api.repository.ai;

import br.com.condominioauditoria.api.model.ai.AiConfigurationEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiConfigurationEventRepository extends JpaRepository<AiConfigurationEvent, UUID> {

    List<AiConfigurationEvent> findByCondominiumIdOrderByOccurredAtAscIdAsc(UUID condominiumId);
}
