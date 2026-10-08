package br.com.condominioauditoria.api.modulo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UsoModuloRepository extends JpaRepository<UsoModulo, UUID> {

    /** Uma linha por mês (no fuso de Brasília), módulo e função, no intervalo [de, ate). */
    interface LinhaMensal {
        String getMes();

        String getModulo();

        String getFuncao();

        Long getQuantidade();

        Long getTokensEntrada();

        Long getTokensSaida();

        Long getArquivos();

        Long getPaginas();
    }

    @Query(nativeQuery = true, value = """
            select to_char(quando at time zone 'America/Sao_Paulo', 'YYYY-MM') as "mes",
                   modulo as "modulo", funcao as "funcao",
                   count(*) as "quantidade",
                   cast(coalesce(sum(tokens_entrada), 0) as bigint) as "tokensEntrada",
                   cast(coalesce(sum(tokens_saida), 0) as bigint) as "tokensSaida",
                   cast(coalesce(sum(arquivos), 0) as bigint) as "arquivos",
                   cast(coalesce(sum(paginas), 0) as bigint) as "paginas"
            from uso_modulo
            where condominio_id = :condominioId and quando >= :de and quando < :ate
            group by 1, 2, 3
            order by 1, 2, 3
            """)
    List<LinhaMensal> totaisPorMes(UUID condominioId, Instant de, Instant ate);

    /** Tokens por mês, módulo, função, provedor e modelo, para o custo estimado (só registros com tokens). */
    interface LinhaTokens {
        String getMes();

        String getModulo();

        String getFuncao();

        String getProvedor();

        String getModelo();

        Long getTokensEntrada();

        Long getTokensSaida();
    }

    @Query(nativeQuery = true, value = """
            select to_char(quando at time zone 'America/Sao_Paulo', 'YYYY-MM') as "mes",
                   modulo as "modulo", funcao as "funcao", provedor as "provedor", modelo as "modelo",
                   cast(coalesce(sum(tokens_entrada), 0) as bigint) as "tokensEntrada",
                   cast(coalesce(sum(tokens_saida), 0) as bigint) as "tokensSaida"
            from uso_modulo
            where condominio_id = :condominioId and quando >= :de and quando < :ate
              and (tokens_entrada is not null or tokens_saida is not null)
            group by 1, 2, 3, 4, 5
            order by 1, 2, 3, 4, 5
            """)
    List<LinhaTokens> tokensPorModelo(UUID condominioId, Instant de, Instant ate);
}
