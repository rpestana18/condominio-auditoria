package br.com.condominioauditoria.api.orcamento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RubricaRepository extends JpaRepository<Rubrica, UUID> {

    List<Rubrica> findByCondominioIdOrderByNome(UUID condominioId);

    Optional<Rubrica> findByIdAndCondominioId(UUID id, UUID condominioId);

    boolean existsByCondominioId(UUID condominioId);
}
