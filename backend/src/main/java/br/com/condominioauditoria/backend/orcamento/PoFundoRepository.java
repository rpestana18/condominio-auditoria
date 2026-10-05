package br.com.condominioauditoria.backend.orcamento;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PoFundoRepository extends JpaRepository<PoFundo, UUID> {
    List<PoFundo> findByPrevisaoId(UUID previsaoId);
}
