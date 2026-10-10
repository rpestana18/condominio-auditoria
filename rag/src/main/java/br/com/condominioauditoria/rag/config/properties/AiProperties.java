package br.com.condominioauditoria.rag.config.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Catalog of AI providers and models, configuration only (block {@code condominio.ai} of application.yml, ADR 0003,
 * Decision 1 and RF-09.6). The rag is the one that knows the implementations; the api reads this catalog through the
 * {@code ListProviders} rpc to build the admin screen and validate what is saved.
 *
 * Adding a provider of an already implemented type ({@code anthropic}, {@code ollama}) is configuration only; a new
 * type is code and requires an ADR.
 */
@ConfigurationProperties(prefix = "condominio.ai")
public record AiProperties(List<AiProvider> providers) {

    /** What the provider is for: drafting chat answers or generating embeddings. */
    public enum AiFunction {
        ANSWERS, EMBEDDINGS
    }

    public record AiProvider(String code, String name, String type, AiFunction function, boolean local, boolean requiresKey,
            int dimension, List<AiModel> models) {
    }

    /**
     * Prices in dollars per million tokens, as decimal text with a dot ("2.00"): protobuf has no exact decimal and
     * the estimated cost of the report is computed in the api with BigDecimal. "0" for a local model.
     */
    public record AiModel(String id, String name, boolean isDefault, String inputPricePerMillionUsd,
            String outputPricePerMillionUsd) {
    }
}
