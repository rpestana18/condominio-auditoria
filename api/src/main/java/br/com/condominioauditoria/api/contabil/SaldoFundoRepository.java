package br.com.condominioauditoria.api.contabil;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SaldoFundoRepository extends JpaRepository<SaldoFundo, UUID> {
    List<SaldoFundo> findByArquivoId(UUID arquivoId);

    @Modifying
    @Query("delete from SaldoFundo s where s.arquivoId = :arquivoId")
    void apagarDoArquivo(UUID arquivoId);
}
