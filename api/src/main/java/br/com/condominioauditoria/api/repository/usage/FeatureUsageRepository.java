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
            select to_char(occurred_at at time zone 'America/Sao_Paulo', 'YYYY-MM') as "month",
                   feature as "feature", function as "function",
                   count(*) as "count",
                   cast(coalesce(sum(input_tokens), 0) as bigint) as "inputTokens",
                   cast(coalesce(sum(output_tokens), 0) as bigint) as "outputTokens",
                   cast(coalesce(sum(files), 0) as bigint) as "files",
                   cast(coalesce(sum(pages), 0) as bigint) as "pages"
            from feature_usage
            where condominium_id = :condominiumId and occurred_at >= :from and occurred_at < :to
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
            select to_char(occurred_at at time zone 'America/Sao_Paulo', 'YYYY-MM') as "month",
                   feature as "feature", function as "function", provider as "provider", model as "model",
                   cast(coalesce(sum(input_tokens), 0) as bigint) as "inputTokens",
                   cast(coalesce(sum(output_tokens), 0) as bigint) as "outputTokens"
            from feature_usage
            where condominium_id = :condominiumId and occurred_at >= :from and occurred_at < :to
              and (input_tokens is not null or output_tokens is not null)
            group by 1, 2, 3, 4, 5
            order by 1, 2, 3, 4, 5
            """)
    List<TokenRow> tokensByModel(UUID condominiumId, Instant from, Instant to);
}
