package br.com.condominioauditoria.api.model.usage;

import br.com.condominioauditoria.api.model.enums.AiMode;
import br.com.condominioauditoria.api.model.enums.UsageFunction;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * One operation of a feature (RF-09.7). Insert only (the database rejects update and delete). Never keeps document
 * text, search text or API key; only counts, mode, provider and model.
 */
@Entity
@Immutable
public class FeatureUsage {

    @Id
    private UUID id;
    private UUID condominiumId;
    private String feature;
    private UsageFunction function;
    /** Null for background processing (e.g. indexing). */
    private String username;
    private Instant occurredAt;
    @Enumerated(EnumType.STRING)
    private AiMode mode;
    private String provider;
    private String model;
    private Long inputTokens;
    private Long outputTokens;
    private Integer files;
    private Integer pages;
    private String promptVersion;

    protected FeatureUsage() {
    }

    public FeatureUsage(UUID condominiumId, String feature, UsageFunction function, String username, Instant at,
            AiMode mode,
            String provider, String model, Long inputTokens, Long outputTokens, Integer files, Integer pages,
            String promptVersion) {
        this.id = UUID.randomUUID();
        this.condominiumId = condominiumId;
        this.feature = feature;
        this.function = function;
        this.username = username;
        this.occurredAt = at;
        this.mode = mode;
        this.provider = provider;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.files = files;
        this.pages = pages;
        this.promptVersion = promptVersion;
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

    public UsageFunction getFunction() {
        return function;
    }

    public String getUsername() {
        return username;
    }

    public Instant getOccurredAt() {
        return occurredAt;
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

    public Long getInputTokens() {
        return inputTokens;
    }

    public Long getOutputTokens() {
        return outputTokens;
    }

    public Integer getFiles() {
        return files;
    }

    public Integer getPages() {
        return pages;
    }

    public String getPromptVersion() {
        return promptVersion;
    }
}
