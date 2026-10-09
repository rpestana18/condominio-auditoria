package br.com.condominioauditoria.api.model.ai;

import br.com.condominioauditoria.api.model.enums.AiFunction;
import br.com.condominioauditoria.api.model.enums.AiMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "evento_configuracao_ia")
public class AiConfigurationEvent {

    @Id
    private UUID id;
    @Column(name = "condominio_id")
    private UUID condominiumId;
    @Column(name = "modulo")
    private String feature;
    @Column(name = "funcao")
    @Enumerated(EnumType.STRING)
    private AiFunction function;
    @Column(name = "usuario")
    private String username;
    @Column(name = "quando")
    private Instant occurredAt;
    @Column(name = "modo_anterior")
    @Enumerated(EnumType.STRING)
    private AiMode previousMode;
    @Column(name = "modo_novo")
    @Enumerated(EnumType.STRING)
    private AiMode newMode;
    @Column(name = "provedor_anterior")
    private String previousProvider;
    @Column(name = "provedor_novo")
    private String newProvider;
    @Column(name = "modelo_anterior")
    private String previousModel;
    @Column(name = "modelo_novo")
    private String newModel;
    @Column(name = "chave_trocada")
    private boolean keyReplaced;
    @Column(name = "chave_final")
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
