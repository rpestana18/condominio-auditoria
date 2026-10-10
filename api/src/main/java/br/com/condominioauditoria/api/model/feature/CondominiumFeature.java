package br.com.condominioauditoria.api.model.feature;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Current state of a feature in a condominium. No row = the catalog default applies. History lives in FeatureEvent. */
@Entity
public class CondominiumFeature {

    @EmbeddedId
    private Key key;
    private boolean enabled;
    private Instant since;
    private String changedBy;

    protected CondominiumFeature() {
    }

    public CondominiumFeature(UUID condominiumId, String feature, boolean enabled, Instant since, String changedBy) {
        this.key = new Key(condominiumId, feature);
        this.enabled = enabled;
        this.since = since;
        this.changedBy = changedBy;
    }

    public void change(boolean enabled, Instant at, String username) {
        this.enabled = enabled;
        this.since = at;
        this.changedBy = username;
    }

    public UUID getCondominiumId() {
        return key.condominiumId;
    }

    public String getFeature() {
        return key.feature;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getSince() {
        return since;
    }

    public String getChangedBy() {
        return changedBy;
    }

    @Embeddable
    public static class Key implements Serializable {

        private UUID condominiumId;
        private String feature;

        protected Key() {
        }

        public Key(UUID condominiumId, String feature) {
            this.condominiumId = condominiumId;
            this.feature = feature;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key c && Objects.equals(condominiumId, c.condominiumId)
                    && Objects.equals(feature, c.feature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(condominiumId, feature);
        }
    }
}
