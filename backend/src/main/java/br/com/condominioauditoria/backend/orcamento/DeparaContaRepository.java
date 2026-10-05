package br.com.condominioauditoria.backend.orcamento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeparaContaRepository extends JpaRepository<DeparaConta, UUID> {

    List<DeparaConta> findByPrevisaoIdOrderByContaCodigo(UUID previsaoId);

    Optional<DeparaConta> findByPrevisaoIdAndContaCodigo(UUID previsaoId, String contaCodigo);
}
