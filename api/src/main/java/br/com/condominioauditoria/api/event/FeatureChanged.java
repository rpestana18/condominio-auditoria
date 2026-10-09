package br.com.condominioauditoria.api.event;

import java.util.UUID;

/**
 * Published inside the transaction that enables or disables a feature. Whoever needs to react (e.g. reindexing when the
 * Assistant is enabled, RF-10.4) listens to this event without the feature code knowing about files.
 */
public record FeatureChanged(UUID condominiumId, String feature, boolean enabled, String username) {
}
