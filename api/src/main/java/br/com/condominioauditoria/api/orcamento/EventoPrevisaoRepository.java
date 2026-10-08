package br.com.condominioauditoria.api.orcamento;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoPrevisaoRepository extends JpaRepository<EventoPrevisao, UUID> {
    List<EventoPrevisao> findByPrevisaoIdOrderByEm(UUID previsaoId);
}
