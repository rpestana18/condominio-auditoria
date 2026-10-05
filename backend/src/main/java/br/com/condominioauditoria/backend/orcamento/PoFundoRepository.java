package br.com.condominioauditoria.backend.orcamento;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PoFundoRepository extends JpaRepository<PoFundo, UUID> {
    List<PoFundo> findByPrevisaoId(UUID previsaoId);

    /**
     * Apaga a ligação atual (o estado; a trilha fica no evento_previsao) já no banco, antes das novas inserções: as
     * chaves únicas por linha e por fundo não podem colidir no meio da troca.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from PoFundo p where p.previsaoId = :previsaoId")
    void apagarDaPrevisao(UUID previsaoId);
}
