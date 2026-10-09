package br.com.condominioauditoria.api.model.usage;

import br.com.condominioauditoria.api.model.enums.AiMode;
import br.com.condominioauditoria.api.model.enums.UsageFunction;
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
 * One operation of a feature (RF-09.7). Insert only (the database rejects update and delete). Never keeps document
 * text, search text or API key; only counts, mode, provider and model.
 */
@Entity
@Immutable
@Table(name = "uso_modulo")
public class FeatureUsage {

    @Id
    private UUID id;
    @Column(name = "condominio_id")
    private UUID condominiumId;
    @Column(name = "modulo")
    private String feature;
    @Column(name = "funcao")
    private UsageFunction function;
    /** Null for background processing (e.g. indexing). */
    @Column(name = "usuario")
    private String username;
    @Column(name = "quando")
    private Instant occurredAt;
    @Column(name = "modo")
    @Enumerated(EnumType.STRING)
    private AiMode mode;
    @Column(name = "provedor")
    private String provider;
    @Column(name = "modelo")
    private String model;
    @Column(name = "tokens_entrada")
    private Long inputTokens;
    @Column(name = "tokens_saida")
    private Long outputTokens;
    @Column(name = "arquivos")
    private Integer files;
    @Column(name = "paginas")
    private Integer pages;
    @Column(name = "versao_prompt")
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
