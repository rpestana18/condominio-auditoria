package br.com.condominioauditoria.backend.orcamento;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoDeparaRepository extends JpaRepository<EventoDepara, UUID> {
    List<EventoDepara> findByPrevisaoIdOrderByEmAscContaCodigoAsc(UUID previsaoId);
}
