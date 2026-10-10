package br.com.condominioauditoria.api.model.ai;

import br.com.condominioauditoria.api.model.enums.AiFunction;
import br.com.condominioauditoria.api.model.enums.AiMode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/**
 * A row of the condominium's AI configuration (table ai_configuration): null feature = general mode; ASSISTENTE +
 * RESPOSTAS = chat (null mode = inherits the general one); ASSISTENTE + EMBEDDINGS = semantic search.
 *
 * The API key exists only encrypted with the rag's public key (the api cannot read it) and never appears in toString,
 * logs or API responses; keySuffix keeps the last 4 characters for the screen.
 */
@Entity
public class AiConfiguration {

    @Id
    private UUID id;
    private UUID condominiumId;
    private String feature;
    @Enumerated(EnumType.STRING)
    private AiFunction function;
    @Enumerated(EnumType.STRING)
    private AiMode mode;
    private String provider;
    private String model;
    private byte[] encryptedKey;
    private String keySuffix;
    private String updatedBy;
    private Instant updatedAt;

    protected AiConfiguration() {
    }

    public AiConfiguration(UUID condominiumId, String feature, AiFunction function) {
        this.id = UUID.randomUUID();
        this.condominiumId = condominiumId;
        this.feature = feature;
        this.function = function;
    }

    public void change(AiMode mode, String provider, String model, String username, Instant updatedAt) {
        this.mode = mode;
        this.provider = provider;
        this.model = model;
        this.updatedBy = username;
        this.updatedAt = updatedAt;
    }

    public void replaceKey(byte[] encryptedKey, String keySuffix) {
        this.encryptedKey = encryptedKey == null ? null : encryptedKey.clone();
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

    public AiMode getMode() {
        return mode;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public byte[] getEncryptedKey() {
        return encryptedKey == null ? null : encryptedKey.clone();
    }

    public boolean hasKey() {
        return encryptedKey != null && encryptedKey.length > 0;
    }

    public String getKeySuffix() {
        return keySuffix;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "AiConfiguration[" + condominiumId + ", " + feature + ", " + function + ", " + mode + ", "
                + provider + "/" + model + ", chave " + (hasKey() ? "cadastrada" : "ausente") + "]";
    }
}
