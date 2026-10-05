package br.com.condominioauditoria.backend.modulo;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ModuloCondominioRepository extends JpaRepository<ModuloCondominio, ModuloCondominio.Chave> {

    @Query("select m from ModuloCondominio m where m.chave.condominioId = :condominioId")
    List<ModuloCondominio> doCondominio(UUID condominioId);

    /**
     * Serializa as alterações de um módulo num condomínio até o fim da transação (lock consultivo do PostgreSQL).
     * Vale mesmo quando a linha ainda não existe: na primeira ligação, dois pedidos ao mesmo tempo não tentam incluir
     * a mesma linha (o segundo espera, lê a linha já gravada e não muda nada).
     */
    @Query(nativeQuery = true, value = """
            select 1 from (select pg_advisory_xact_lock(hashtextextended('modulo_condominio:' || :condominioId || ':' || :modulo, 0))) t
            """)
    Integer serializarAlteracao(String condominioId, String modulo);

    /** Trava a linha durante a alteração: dois pedidos ao mesmo tempo não gravam dois eventos iguais. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ModuloCondominio m where m.chave = :chave")
    Optional<ModuloCondominio> travar(ModuloCondominio.Chave chave);
}
