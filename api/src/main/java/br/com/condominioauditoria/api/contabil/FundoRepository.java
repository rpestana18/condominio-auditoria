package br.com.condominioauditoria.api.contabil;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundoRepository extends JpaRepository<Fundo, UUID> {
    Optional<Fundo> findByCondominioIdAndNome(UUID condominioId, String nome);

    List<Fundo> findByCondominioId(UUID condominioId);
}
