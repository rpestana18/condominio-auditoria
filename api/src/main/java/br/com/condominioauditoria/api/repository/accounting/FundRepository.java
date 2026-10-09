package br.com.condominioauditoria.api.repository.accounting;

import br.com.condominioauditoria.api.model.accounting.Fund;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundRepository extends JpaRepository<Fund, UUID> {
    Optional<Fund> findByCondominiumIdAndName(UUID condominiumId, String name);

    List<Fund> findByCondominiumId(UUID condominiumId);
}
