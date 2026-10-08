package br.com.condominioauditoria.backend.orcamento;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinhaRubricaRepository extends JpaRepository<LinhaRubrica, UUID> {

    List<LinhaRubrica> findByPrevisaoId(UUID previsaoId);

    List<LinhaRubrica> findByCondominioIdAndEstado(UUID condominioId, EstadoRubrica estado);
}
