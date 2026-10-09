package br.com.condominioauditoria.api.service.feature;

import br.com.condominioauditoria.api.config.properties.FeatureCatalog;
import br.com.condominioauditoria.api.config.properties.FeatureCatalog.FeatureDefinition;
import br.com.condominioauditoria.api.event.FeatureChanged;
import br.com.condominioauditoria.api.exception.FeatureNotEnabledException;
import br.com.condominioauditoria.api.exception.InvalidRequestException;
import br.com.condominioauditoria.api.model.feature.ActivePeriod;
import br.com.condominioauditoria.api.model.feature.CondominiumFeature;
import br.com.condominioauditoria.api.model.feature.FeatureEvent;
import br.com.condominioauditoria.api.repository.feature.CondominiumFeatureRepository;
import br.com.condominioauditoria.api.repository.feature.FeatureEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central check of the contractable features (RF-10; ADR 0003, Decision 4). Every entry point that belongs to a feature
 * calls {@link #require} (rejects) or {@link #isEnabled} (skips silently, e.g. indexing). The state comes from the
 * database on every call: enabling or disabling applies on the next request, without a restart (RF-10.2).
 *
 * The ADMIN role changes it, checked in the API (FeatureController). In the MVP the Keycloak ADMIN is the platform
 * administrator ("everything, in every condominium", see CondominiumAccess) and plays the Super-admin of RF-10.2. When
 * per-condominium roles exist, the condominium Admin can no longer change it and only the Super-admin can.
 */
@Service
public class FeatureService {

    /** Code of the Assistant feature in the catalog (catalogo-modulos.yml). */
    public static final String ASSISTANT = "ASSISTENTE";
    public static final int MAX_REASON_LENGTH = 500;

    private static final Logger log = LoggerFactory.getLogger(FeatureService.class);

    private final FeatureCatalog catalog;
    private final CondominiumFeatureRepository states;
    private final FeatureEventRepository events;
    private final ApplicationEventPublisher publisher;

    FeatureService(FeatureCatalog catalog, CondominiumFeatureRepository states, FeatureEventRepository events,
            ApplicationEventPublisher publisher) {
        this.catalog = catalog;
        this.states = states;
        this.events = events;
        this.publisher = publisher;
        if (catalog.find(ASSISTANT).isEmpty()) {
            throw new IllegalStateException("O catálogo de módulos não tem o " + ASSISTANT);
        }
    }

    /** State of a catalog feature in the condominium, with what it includes. */
    public record FeatureState(FeatureDefinition definition, boolean enabled, Instant since, int catalogVersion) {
    }

    public FeatureCatalog catalog() {
        return catalog;
    }

    /**
     * Enabled in the condominium? No row in the database = catalog default. A feature outside the catalog is never
     * enabled.
     */
    public boolean isEnabled(UUID condominiumId, String feature) {
        Optional<FeatureDefinition> definition = catalog.find(feature);
        if (definition.isEmpty()) {
            return false;
        }
        return states.findById(new CondominiumFeature.Key(condominiumId, feature))
                .map(CondominiumFeature::isEnabled)
                .orElse(definition.get().enabledByDefault());
    }

    /** Rejects with "Módulo X não contratado para este condomínio." if the feature is disabled (RF-10.3). */
    public void require(UUID condominiumId, String feature) {
        if (!isEnabled(condominiumId, feature)) {
            FeatureDefinition definition = catalog.require(feature);
            throw new FeatureNotEnabledException(definition.code(), definition.name());
        }
    }

    /** Every catalog feature, in catalog order, with its state in the condominium. */
    @Transactional(readOnly = true)
    public List<FeatureState> states(UUID condominiumId) {
        Map<String, CondominiumFeature> saved = states.findByCondominium(condominiumId).stream()
                .collect(Collectors.toMap(CondominiumFeature::getFeature, Function.identity()));
        return catalog.features().stream().map(d -> state(d, Optional.ofNullable(saved.get(d.code())))).toList();
    }

    /** Codes of the features enabled in the condominium, in catalog order. */
    public List<String> enabledCodes(UUID condominiumId) {
        return states(condominiumId).stream().filter(FeatureState::enabled).map(e -> e.definition().code()).toList();
    }

    /**
     * Enables or disables (RF-10.2) and saves the event in the trail with the optional reason (RF-10.6). Asking for the
     * state already in force saves nothing. Enabling the Assistant reindexes the condominium's files after the commit
     * (RF-10.4, by whoever listens to {@link FeatureChanged}); disabling deletes neither the index nor the originals
     * (RF-10.5).
     */
    @Transactional
    public FeatureState change(UUID condominiumId, String feature, boolean enable, String reason, String username) {
        FeatureDefinition definition = catalog.require(feature);
        String trimmedReason = reason == null || reason.isBlank() ? null : reason.strip();
        if (trimmedReason != null && trimmedReason.length() > MAX_REASON_LENGTH) {
            throw new InvalidRequestException("O motivo passa de " + MAX_REASON_LENGTH + " caracteres");
        }
        var key = new CondominiumFeature.Key(condominiumId, feature);
        states.serializeChange(condominiumId.toString(), feature);
        Optional<CondominiumFeature> current = states.lockByKey(key);
        boolean before = current.map(CondominiumFeature::isEnabled).orElse(definition.enabledByDefault());
        if (before == enable) {
            return state(definition, current);
        }
        Instant now = Instant.now();
        CondominiumFeature row = current.orElseGet(() -> new CondominiumFeature(condominiumId, feature, enable, now,
                username));
        row.change(enable, now, username);
        row = states.save(row);
        events.save(new FeatureEvent(condominiumId, feature, before, enable, username, now, trimmedReason));
        publisher.publishEvent(new FeatureChanged(condominiumId, feature, enable, username));
        log.info("Módulo {} {} no condomínio {} por {} (motivo: {})", feature, enable ? "ligado" : "desligado",
                condominiumId, username, trimmedReason == null ? "não informado" : trimmedReason);
        return state(definition, Optional.of(row));
    }

    /** Activation trail of the feature in the condominium, oldest first. */
    @Transactional(readOnly = true)
    public List<FeatureEvent> events(UUID condominiumId, String feature) {
        catalog.require(feature);
        return events.findByCondominiumIdAndFeatureOrderByOccurredAtAscIdAsc(condominiumId, feature);
    }

    /** Active periods calculated from the trail (RF-10.6). */
    @Transactional(readOnly = true)
    public List<ActivePeriod> periods(UUID condominiumId, String feature) {
        FeatureDefinition definition = catalog.require(feature);
        return ActivePeriod.calculate(feature, definition.enabledByDefault(),
                events.findByCondominiumIdAndFeatureOrderByOccurredAtAscIdAsc(condominiumId, feature));
    }

    private FeatureState state(FeatureDefinition definition, Optional<CondominiumFeature> saved) {
        return new FeatureState(definition,
                saved.map(CondominiumFeature::isEnabled).orElse(definition.enabledByDefault()),
                saved.map(CondominiumFeature::getSince).orElse(null), catalog.version());
    }
}
