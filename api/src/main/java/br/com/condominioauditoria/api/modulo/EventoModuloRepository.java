package br.com.condominioauditoria.api.modulo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoModuloRepository extends JpaRepository<EventoModulo, UUID> {

    /** Do mais antigo para o mais recente; o id desempata eventos no mesmo instante (ordem estável). */
    List<EventoModulo> findByCondominioIdAndModuloOrderByQuandoAscIdAsc(UUID condominioId, String modulo);
}
