package br.com.condominioauditoria.api.service.ai;

import br.com.condominioauditoria.api.config.properties.ApiProperties;
import br.com.condominioauditoria.api.exception.AiUnavailableException;
import br.com.condominioauditoria.api.grpc.client.AssistantClient;
import br.com.condominioauditoria.api.model.enums.AiFunction;
import br.com.condominioauditoria.api.service.calculator.UsageCostCalculator.ModelPrice;
import br.com.condominioauditoria.contratos.assistente.v1.ListarProvedoresResponse;
import br.com.condominioauditoria.contratos.assistente.v1.ModeloProvedor;
import br.com.condominioauditoria.contratos.assistente.v1.Provedor;
import io.grpc.StatusRuntimeException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Catalog of AI providers and models, read from the rag (ListarProvedores; RF-09.6, ADR 0003) and kept in memory for a
 * few minutes (condominio.rag.catalog-cache-seconds), so the rag is not called on every screen or save. The catalog is
 * the same for every user; the token only lets the rag accept the call.
 */
@Component
public class AiCatalog {

    private static final Logger log = LoggerFactory.getLogger(AiCatalog.class);
    static final String UNAVAILABLE = "Catálogo de provedores de IA indisponível: o serviço rag não respondeu."
            + " Nada foi gravado; tente de novo em instantes.";

    private final AssistantClient rag;
    private final Duration ttl;
    private final Clock clock;
    private volatile CachedCatalog cached;

    @Autowired
    public AiCatalog(AssistantClient rag, ApiProperties properties) {
        this(rag, Duration.ofSeconds(properties.rag().catalogCacheSeconds()), Clock.systemUTC());
    }

    public AiCatalog(AssistantClient rag, Duration ttl, Clock clock) {
        this.rag = rag;
        this.ttl = ttl;
        this.clock = clock;
    }

    /** A catalog model. Price in US$ per million tokens (null if the rag sent text that is not a decimal). */
    public record AiModel(String id, String name, boolean isDefault, BigDecimal inputPricePerMillionUsd,
            BigDecimal outputPricePerMillionUsd) {
    }

    /** A catalog provider, in the rag's order. dimension only for embeddings. */
    public record AiProvider(String code, String name, String type, AiFunction function, boolean local,
            boolean requiresKey, Integer dimension, List<AiModel> models) {

        public Optional<AiModel> model(String id) {
            return models.stream().filter(m -> m.id().equals(id)).findFirst();
        }

        /** The model marked as default; without a mark, the first one. */
        public Optional<AiModel> defaultModel() {
            return models.stream().filter(AiModel::isDefault).findFirst().or(() -> models.stream().findFirst());
        }
    }

    /** Catalog and the rag's public key (empty = rag without a key pair: no key can be registered). */
    public record Catalog(List<AiProvider> providers, String publicKeyPem) {

        public Optional<AiProvider> provider(String code) {
            return providers.stream().filter(p -> p.code().equals(code)).findFirst();
        }

        /** Prices by "provider/model", for the estimated cost of the usage report. */
        public Map<String, ModelPrice> prices() {
            Map<String, ModelPrice> prices = new LinkedHashMap<>();
            for (AiProvider p : providers) {
                for (AiModel m : p.models()) {
                    if (m.inputPricePerMillionUsd() != null && m.outputPricePerMillionUsd() != null) {
                        prices.put(ModelPrice.key(p.code(), m.id()),
                                new ModelPrice(m.inputPricePerMillionUsd(), m.outputPricePerMillionUsd()));
                    }
                }
            }
            return prices;
        }
    }

    private record CachedCatalog(Catalog catalog, Instant readAt) {
    }

    /** From the cache, if still valid; otherwise asks the rag. Rag down = {@link AiUnavailableException} (503). */
    public Catalog read(String authorization) {
        CachedCatalog current = cached;
        Instant now = clock.instant();
        if (current != null && now.isBefore(current.readAt().plus(ttl))) {
            return current.catalog();
        }
        ListarProvedoresResponse response;
        try {
            response = rag.listProviders(authorization);
        } catch (StatusRuntimeException error) {
            log.warn("Catálogo de IA: rag não respondeu ({}: {})", error.getStatus().getCode(),
                    error.getStatus().getDescription());
            throw new AiUnavailableException(UNAVAILABLE);
        }
        Catalog catalog = convert(response);
        cached = new CachedCatalog(catalog, now);
        return catalog;
    }

    /**
     * Prices for the estimated cost; empty if the rag did not answer (the report comes out without cost, it does not
     * fail).
     */
    public Optional<Map<String, ModelPrice>> prices(String authorization) {
        try {
            return Optional.of(read(authorization).prices());
        } catch (AiUnavailableException error) {
            return Optional.empty();
        }
    }

    static Catalog convert(ListarProvedoresResponse response) {
        List<AiProvider> providers = response.getProvedoresList().stream()
                .filter(p -> {
                    boolean known = function(p) != null;
                    if (!known) {
                        log.warn("Catálogo de IA: provedor {} sem uso informado foi ignorado", p.getCodigo());
                    }
                    return known;
                })
                .map(p -> new AiProvider(p.getCodigo(), p.getNome(), p.getTipo(), function(p), p.getLocal(),
                        p.getPrecisaChave(), p.getDimensao() > 0 ? p.getDimensao() : null,
                        p.getModelosList().stream().map(AiCatalog::model).toList()))
                .toList();
        return new Catalog(providers, response.getChavePublicaPem());
    }

    private static AiFunction function(Provedor p) {
        return switch (p.getUso()) {
            case USO_PROVEDOR_RESPOSTAS -> AiFunction.ANSWERS;
            case USO_PROVEDOR_EMBEDDINGS -> AiFunction.EMBEDDINGS;
            default -> null;
        };
    }

    private static AiModel model(ModeloProvedor m) {
        return new AiModel(m.getId(), m.getNome(), m.getPadrao(), price(m.getPrecoEntradaMilhaoUsd()),
                price(m.getPrecoSaidaMilhaoUsd()));
    }

    /** Exact decimal text ("2.00"); empty = 0 (local model); wrong format = null (cost not calculated). */
    static BigDecimal price(String text) {
        if (text == null || text.isBlank()) {
            return BigDecimal.ZERO;
        }
        if (!text.strip().matches("[0-9]+(\\.[0-9]+)?")) {
            log.warn("Catálogo de IA: preço fora do formato decimal ignorado: {}", text);
            return null;
        }
        return new BigDecimal(text.strip());
    }
}
