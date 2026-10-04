package br.com.condominioauditoria.backend.auditoria;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AchadoEvidenciaRepository extends JpaRepository<AchadoEvidencia, UUID> {

    List<AchadoEvidencia> findByAchadoIdInOrderByOrdemAsc(Collection<UUID> achados);
}
