package br.com.condominioauditoria.backend.auditoria;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AchadoRepository extends JpaRepository<Achado, UUID> {

    Optional<Achado> findByCondominioIdAndRegraAndCompetenciaAndAlvo(UUID condominioId, String regra,
            LocalDate competencia, String alvo);

    List<Achado> findByCondominioIdAndAlvoStartingWithOrderByCriadoEm(UUID condominioId, String prefixoAlvo);
}
