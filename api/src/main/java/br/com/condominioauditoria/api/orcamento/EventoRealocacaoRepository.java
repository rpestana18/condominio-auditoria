package br.com.condominioauditoria.api.orcamento;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoRealocacaoRepository extends JpaRepository<EventoRealocacao, UUID> {
}
