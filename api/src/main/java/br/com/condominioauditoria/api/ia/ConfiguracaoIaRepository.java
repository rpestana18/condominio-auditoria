package br.com.condominioauditoria.api.ia;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConfiguracaoIaRepository extends JpaRepository<ConfiguracaoIa, UUID> {

    List<ConfiguracaoIa> findByCondominioId(UUID condominioId);

    /**
     * Serializa as gravações da configuração de IA de um condomínio até o fim da transação (lock consultivo do
     * PostgreSQL), como em modulo_condominio: dois Admins salvando ao mesmo tempo não gravam linhas nem eventos
     * duplicados (o segundo espera e compara com o que o primeiro gravou).
     */
    @Query(nativeQuery = true, value = """
            select 1 from (select pg_advisory_xact_lock(hashtextextended('configuracao_ia:' || :condominioId, 0))) t
            """)
    Integer serializarAlteracao(String condominioId);
}
