package br.com.condominioauditoria.api.model.ai;

import br.com.condominioauditoria.api.model.enums.AiFunction;
import br.com.condominioauditoria.api.model.enums.AiMode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Audit trail of the AI configuration (RF-09.6, RF-07.4): one row per changed function, insert only (the database
 * rejects update and delete). Never stores the key: only whether it was replaced and the last 4 characters of the new
 * one (keyReplaced with a null keySuffix = key removed).
 */
@Entity
@Immutable
public class AiConfigurationEvent {

    @Id
    private UUID id;
    private UUID condominiumId;
    private String feature;
    @Enumerated(EnumType.STRING)
    private AiFunction function;
    private String username;
    private Instant occurredAt;
    @Enumerated(EnumType.STRING)
    private AiMode previousMode;
    @Enumerated(EnumType.STRING)
    private AiMode newMode;
    private String previousProvider;
    private String newProvider;
    private String previousModel;
    private String newModel;
    private boolean keyReplaced;
    private String keySuffix;

    protected AiConfigurationEvent() {
    }

    public AiConfigurationEvent(UUID condominiumId, String feature, AiFunction function, String username,
            Instant occurredAt,
            AiMode previousMode, AiMode newMode, String previousProvider, String newProvider, String previousModel,
            String newModel, boolean keyReplaced, String keySuffix) {
        this.id = UUID.randomUUID();
        this.condominiumId = condominiumId;
        this.feature = feature;
        this.function = function;
        this.username = username;
        this.occurredAt = occurredAt;
        this.previousMode = previousMode;
        this.newMode = newMode;
        this.previousProvider = previousProvider;
        this.newProvider = newProvider;
        this.previousModel = previousModel;
        this.newModel = newModel;
        this.keyReplaced = keyReplaced;
        this.keySuffix = keySuffix;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public String getFeature() {
        return feature;
    }

    public AiFunction getFunction() {
        return function;
    }

    public String getUsername() {
        return username;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public AiMode getPreviousMode() {
        return previousMode;
    }

    public AiMode getNewMode() {
        return newMode;
    }

    public String getPreviousProvider() {
        return previousProvider;
    }

    public String getNewProvider() {
        return newProvider;
    }

    public String getPreviousModel() {
        return previousModel;
    }

    public String getNewModel() {
        return newModel;
    }

    public boolean isKeyReplaced() {
        return keyReplaced;
    }

    public String getKeySuffix() {
        return keySuffix;
    }
}
