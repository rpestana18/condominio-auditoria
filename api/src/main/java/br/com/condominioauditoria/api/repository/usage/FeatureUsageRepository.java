package br.com.condominioauditoria.api.repository.usage;

import br.com.condominioauditoria.api.model.usage.FeatureUsage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FeatureUsageRepository extends JpaRepository<FeatureUsage, UUID> {

    /** One row per month (Brasília time), feature and function, in the interval [from, to). */
    interface MonthlyRow {
        String getMonth();

        String getFeature();

        String getFunction();

        Long getCount();

        Long getInputTokens();

        Long getOutputTokens();

        Long getFiles();

        Long getPages();
    }

    @Query(nativeQuery = true, value = """
            select to_char(quando at time zone 'America/Sao_Paulo', 'YYYY-MM') as "month",
                   modulo as "feature", funcao as "function",
                   count(*) as "count",
                   cast(coalesce(sum(tokens_entrada), 0) as bigint) as "inputTokens",
                   cast(coalesce(sum(tokens_saida), 0) as bigint) as "outputTokens",
                   cast(coalesce(sum(arquivos), 0) as bigint) as "files",
                   cast(coalesce(sum(paginas), 0) as bigint) as "pages"
            from uso_modulo
            where condominio_id = :condominiumId and quando >= :from and quando < :to
            group by 1, 2, 3
            order by 1, 2, 3
            """)
    List<MonthlyRow> monthlyTotals(UUID condominiumId, Instant from, Instant to);

    /** Tokens per month, feature, function, provider and model, for the estimated cost (only records with tokens). */
    interface TokenRow {
        String getMonth();

        String getFeature();

        String getFunction();

        String getProvider();

        String getModel();

        Long getInputTokens();

        Long getOutputTokens();
    }

    @Query(nativeQuery = true, value = """
            select to_char(quando at time zone 'America/Sao_Paulo', 'YYYY-MM') as "month",
                   modulo as "feature", funcao as "function", provedor as "provider", modelo as "model",
                   cast(coalesce(sum(tokens_entrada), 0) as bigint) as "inputTokens",
                   cast(coalesce(sum(tokens_saida), 0) as bigint) as "outputTokens"
            from uso_modulo
            where condominio_id = :condominiumId and quando >= :from and quando < :to
              and (tokens_entrada is not null or tokens_saida is not null)
            group by 1, 2, 3, 4, 5
            order by 1, 2, 3, 4, 5
            """)
    List<TokenRow> tokensByModel(UUID condominiumId, Instant from, Instant to);
}
