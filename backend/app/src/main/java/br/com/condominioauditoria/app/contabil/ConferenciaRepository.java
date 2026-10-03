package br.com.condominioauditoria.app.contabil;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ConferenciaRepository extends JpaRepository<Conferencia, UUID> {
    List<Conferencia> findByArquivoIdOrderByOrdem(UUID arquivoId);

    @Modifying
    @Query("delete from Conferencia c where c.arquivoId = :arquivoId")
    void apagarDoArquivo(UUID arquivoId);
}
