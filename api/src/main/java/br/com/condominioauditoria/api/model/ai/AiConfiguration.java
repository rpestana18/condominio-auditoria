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

/**
 * A row of the condominium's AI configuration (table configuracao_ia, V13): null feature = general mode; ASSISTENTE +
 * RESPOSTAS = chat (null mode = inherits the general one); ASSISTENTE + EMBEDDINGS = semantic search.
 *
 * The API key exists only encrypted with the rag's public key (the api cannot read it) and never appears in toString,
 * logs or API responses; keySuffix keeps the last 4 characters for the screen.
 */
@Entity
@Table(name = "configuracao_ia")
public class AiConfiguration {

    @Id
    private UUID id;
    @Column(name = "condominio_id")
    private UUID condominiumId;
    @Column(name = "modulo")
    private String feature;
    @Column(name = "funcao")
    @Enumerated(EnumType.STRING)
    private AiFunction function;
    @Column(name = "modo")
    @Enumerated(EnumType.STRING)
    private AiMode mode;
    @Column(name = "provedor")
    private String provider;
    @Column(name = "modelo")
    private String model;
    @Column(name = "chave_cifrada")
    private byte[] encryptedKey;
    @Column(name = "chave_final")
    private String keySuffix;
    @Column(name = "atualizado_por")
    private String updatedBy;
    @Column(name = "atualizado_em")
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
        return "AiConfiguration[" + condominiumId + ", " + feature + ", " + function + ", " + mode + ", " + provider + "/"
                + model + ", chave " + (hasKey() ? "cadastrada" : "ausente") + "]";
    }
}
