package br.com.condominioauditoria.backend.orcamento;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LinhaPoRepository extends JpaRepository<LinhaPo, UUID> {

    List<LinhaPo> findByPrevisaoIdOrderByOrdem(UUID previsaoId);

    @Modifying
    @Query("delete from LinhaPo l where l.previsaoId = :previsaoId")
    void apagarDaPrevisao(UUID previsaoId);
}
