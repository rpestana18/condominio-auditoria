package br.com.condominioauditoria.backend.orcamento;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrevisaoOrcamentariaRepository extends JpaRepository<PrevisaoOrcamentaria, UUID> {

    Optional<PrevisaoOrcamentaria> findByArquivoId(UUID arquivoId);

    Optional<PrevisaoOrcamentaria> findByIdAndCondominioId(UUID id, UUID condominioId);

    List<PrevisaoOrcamentaria> findByCondominioIdOrderByLidaEmDesc(UUID condominioId);

    List<PrevisaoOrcamentaria> findByCondominioIdAndEstadoIn(UUID condominioId, Collection<EstadoPrevisao> estados);
}
