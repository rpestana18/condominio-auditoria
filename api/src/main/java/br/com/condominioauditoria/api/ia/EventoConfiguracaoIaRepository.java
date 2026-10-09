package br.com.condominioauditoria.api.ia;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoConfiguracaoIaRepository extends JpaRepository<EventoConfiguracaoIa, UUID> {

    List<EventoConfiguracaoIa> findByCondominioIdOrderByQuandoAscIdAsc(UUID condominioId);
}
