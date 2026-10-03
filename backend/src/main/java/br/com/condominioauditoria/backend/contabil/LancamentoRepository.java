package br.com.condominioauditoria.backend.contabil;

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

    /**
     * Consulta com filtros para o gRPC (mcp). Sem nulos nos parâmetros: quem chama passa datas-limite largas,
     * texto vazio e a lista de fundos já resolvida.
     */
    @Query("""
            select l from Lancamento l
            where l.condominioId = :condominioId
              and l.data >= :inicio and l.data <= :fim
              and (:todosFundos = true or l.fundoId in :fundos)
              and (:texto = '' or lower(l.historico) like :texto
                   or lower(coalesce(l.fornecedor, '')) like :texto
                   or lower(coalesce(l.contaNome, '')) like :texto)
              and (:somenteSaidas = false or (l.debito > 0 and l.transferenciaEntreFundos = false))
            order by l.data, l.arquivoId, l.ordem""")
    List<Lancamento> filtrar(UUID condominioId, java.time.LocalDate inicio, java.time.LocalDate fim,
            boolean todosFundos, java.util.Collection<UUID> fundos, String texto, boolean somenteSaidas, Limit limite);

    @Modifying
    @Query("delete from Lancamento l where l.arquivoId = :arquivoId")
    void apagarDoArquivo(UUID arquivoId);
}
