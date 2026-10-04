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

    /** Trava a linha durante a alteração: dois pedidos ao mesmo tempo não gravam dois eventos iguais. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ModuloCondominio m where m.chave = :chave")
    Optional<ModuloCondominio> travar(ModuloCondominio.Chave chave);
}
