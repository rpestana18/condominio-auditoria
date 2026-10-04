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

    /**
     * Débitos de um fundo no período, só de arquivos da categoria de balancetes e fluxos de caixa, sem as
     * transferências entre fundos e só com conta do fluxo: as contas que entram no de-para (RF-03.1.4).
     */
    @Query("""
            select l from Lancamento l
            where l.condominioId = :condominioId and l.fundoId = :fundoId
              and l.data >= :inicio and l.data <= :fim
              and l.debito <> 0 and l.transferenciaEntreFundos = false and l.contaCodigo is not null
              and l.arquivoId in (select a.id from Arquivo a
                                  where a.categoria = br.com.condominioauditoria.backend.arquivo.Categoria.BALANCETE)
            order by l.data, l.arquivoId, l.ordem""")
    List<Lancamento> debitosComConta(UUID condominioId, UUID fundoId, java.time.LocalDate inicio,
            java.time.LocalDate fim);

    /** Lançamentos dos fluxos escolhidos no período, de todos os fundos (previsto × realizado). */
    List<Lancamento> findByArquivoIdInAndDataBetween(java.util.Collection<UUID> arquivos, java.time.LocalDate inicio,
            java.time.LocalDate fim);

    @Modifying
    @Query("delete from Lancamento l where l.arquivoId = :arquivoId")
    void apagarDoArquivo(UUID arquivoId);
}
