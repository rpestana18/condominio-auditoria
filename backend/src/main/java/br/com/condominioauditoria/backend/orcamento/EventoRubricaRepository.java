package br.com.condominioauditoria.backend.orcamento;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoRubricaRepository extends JpaRepository<EventoRubrica, UUID> {
    List<EventoRubrica> findByPrevisaoIdOrderByEmAscLinhaCodigoAsc(UUID previsaoId);
}
