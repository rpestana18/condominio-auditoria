package br.com.condominioauditoria.app.contabil;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LancamentoRepository extends JpaRepository<Lancamento, UUID> {
    List<Lancamento> findByArquivoIdOrderByOrdem(UUID arquivoId);

    /** Maiores saídas reais do período (sem transferências entre fundos). */
    List<Lancamento> findByArquivoIdAndTransferenciaEntreFundosFalseOrderByDebitoDesc(UUID arquivoId, Limit limite);

    @Modifying
    @Query("delete from Lancamento l where l.arquivoId = :arquivoId")
    void apagarDoArquivo(UUID arquivoId);
}
