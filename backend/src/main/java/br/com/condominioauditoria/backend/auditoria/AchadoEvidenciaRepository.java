package br.com.condominioauditoria.backend.auditoria;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AchadoEvidenciaRepository extends JpaRepository<AchadoEvidencia, UUID> {
}
