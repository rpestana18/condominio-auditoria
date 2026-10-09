package br.com.condominioauditoria.api.repository.ai;

import br.com.condominioauditoria.api.model.ai.AiConfiguration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiConfigurationRepository extends JpaRepository<AiConfiguration, UUID> {

    List<AiConfiguration> findByCondominiumId(UUID condominiumId);

    /**
     * Serializes the writes of a condominium's AI configuration until the end of the transaction (PostgreSQL advisory
     * lock), as in modulo_condominio: two Admins saving at the same time do not write duplicate rows or events (the
     * second waits and compares with what the first saved).
     */
    @Query(nativeQuery = true, value = """
            select 1 from (select pg_advisory_xact_lock(hashtextextended('configuracao_ia:' || :condominiumId, 0))) t
            """)
    Integer lockForUpdate(String condominiumId);
}
