package br.com.condominioauditoria.api.service.ai;

import br.com.condominioauditoria.api.dto.response.feature.AssistantContextResponse;
import br.com.condominioauditoria.api.exception.AiConfigurationRejectedException;
import br.com.condominioauditoria.api.exception.AiUnavailableException;
import br.com.condominioauditoria.api.exception.InvalidRequestException;
import br.com.condominioauditoria.api.model.ai.AiConfiguration;
import br.com.condominioauditoria.api.model.ai.AiConfigurationEvent;
import br.com.condominioauditoria.api.model.enums.AiFunction;
import br.com.condominioauditoria.api.model.enums.AiMode;
import br.com.condominioauditoria.api.repository.ai.AiConfigurationEventRepository;
import br.com.condominioauditoria.api.repository.ai.AiConfigurationRepository;
import br.com.condominioauditoria.api.security.ApiKeyCipher;
import br.com.condominioauditoria.api.service.ai.AiCatalog.AiModel;
import br.com.condominioauditoria.api.service.ai.AiCatalog.AiProvider;
import br.com.condominioauditoria.api.service.ai.AiCatalog.Catalog;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * The condominium's AI configuration (RF-09.1, RF-09.2, RF-09.6; ADR 0003, Decision 4 and Sub-decision 4.1 A).
 *
 * <ul>
 * <li>General mode: no row = MCP_EXTERNO (the pilot's default).</li>
 * <li>Assistant answers (chat): null mode = inherits the general mode; provider, model and encrypted key.</li>
 * <li>Assistant embeddings: only LOCAL (local catalog provider) or DESLIGADO in this phase (Q12); no row = LOCAL with
 * ollama-local/bge-m3.</li>
 * </ul>
 *
 * The API key arrives in plain text only on the PUT, is encrypted right away with the rag's public key and never comes
 * back, never goes to a log or to the audit trail (only "key replaced" and the last 4 characters). Who changes it
 * (ADMIN) is checked in the API.
 */
@Service
public class AiConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(AiConfigurationService.class);

    public static final AiMode DEFAULT_GENERAL_MODE = AiMode.MCP_EXTERNO;
    public static final AiMode DEFAULT_EMBEDDINGS_MODE = AiMode.LOCAL;
    public static final String DEFAULT_EMBEDDINGS_PROVIDER = "ollama-local";
    public static final String DEFAULT_EMBEDDINGS_MODEL = "bge-m3";
    static final int KEY_MIN_LENGTH = 8;
    static final int KEY_MAX_LENGTH = 500;

    static final String NO_PUBLIC_KEY = "O serviço rag está sem chave pública para cifrar a chave de IA."
            + " Nada foi gravado; avise o suporte da instalação.";

    private final AiConfigurationRepository configurations;
    private final AiConfigurationEventRepository events;
    private final AiCatalog catalog;
    private final FeatureService features;
    private final TransactionOperations transaction;

    public AiConfigurationService(AiConfigurationRepository configurations, AiConfigurationEventRepository events,
            AiCatalog catalog, FeatureService features, TransactionOperations transaction) {
        this.configurations = configurations;
        this.events = events;
        this.catalog = catalog;
        this.features = features;
        this.transaction = transaction;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Leitura
    // ---------------------------------------------------------------------------------------------------------------

    /** Effective configuration, with the defaults applied. Null updatedBy/At = never saved. */
    public record Effective(AiMode generalMode, Answers answers, Embeddings embeddings, String updatedBy,
            Instant updatedAt) {
    }

    /**
     * Assistant answers. Null mode = inherits the general one. The key exists only encrypted (the api does not read
     * it).
     */
    public record Answers(AiMode mode, AiMode effectiveMode, String provider, String model, byte[] encryptedKey,
            String keySuffix) {

        public boolean hasKey() {
            return encryptedKey != null && encryptedKey.length > 0;
        }

        /** Chat on the screen: effective mode API_KEY with a registered key (and a chosen provider). */
        public boolean chatAvailable() {
            return effectiveMode == AiMode.API_KEY && hasKey() && provider != null;
        }

        @Override
        public String toString() {
            return "Answers[" + mode + "/" + effectiveMode + ", " + provider + "/" + model + ", chave "
                    + (hasKey() ? "cadastrada" : "ausente") + "]";
        }
    }

    public record Embeddings(AiMode mode, String provider, String model) {
    }

    public Effective read(UUID condominiumId) {
        return effective(configurations.findByCondominiumId(condominiumId));
    }

    public AssistantContextResponse assistantContext(UUID condominiumId) {
        if (!features.isEnabled(condominiumId, FeatureService.ASSISTANT)) {
            return null;
        }
        Effective e = read(condominiumId);
        return new AssistantContextResponse(e.answers().effectiveMode(), e.embeddings().mode(),
                e.answers().chatAvailable());
    }

    static Effective effective(List<AiConfiguration> rows) {
        Optional<AiConfiguration> general = row(rows, null, AiFunction.RESPOSTAS);
        Optional<AiConfiguration> answers = row(rows, FeatureService.ASSISTANT, AiFunction.RESPOSTAS);
        Optional<AiConfiguration> embeddings = row(rows, FeatureService.ASSISTANT, AiFunction.EMBEDDINGS);
        AiMode generalMode = general.map(AiConfiguration::getMode).orElse(DEFAULT_GENERAL_MODE);
        AiMode answersMode = answers.map(AiConfiguration::getMode).orElse(null);
        Answers r = new Answers(answersMode, answersMode == null ? generalMode : answersMode,
                answers.map(AiConfiguration::getProvider).orElse(null),
                answers.map(AiConfiguration::getModel).orElse(null),
                answers.map(AiConfiguration::getEncryptedKey).orElse(null),
                answers.map(AiConfiguration::getKeySuffix).orElse(null));
        Embeddings e = embeddings.map(l -> new Embeddings(l.getMode(), l.getProvider(), l.getModel()))
                .orElse(new Embeddings(DEFAULT_EMBEDDINGS_MODE, DEFAULT_EMBEDDINGS_PROVIDER, DEFAULT_EMBEDDINGS_MODEL));
        Optional<AiConfiguration> latest = rows.stream().filter(l -> l.getUpdatedAt() != null)
                .max(Comparator.comparing(AiConfiguration::getUpdatedAt));
        return new Effective(generalMode, r, e, latest.map(AiConfiguration::getUpdatedBy).orElse(null),
                latest.map(AiConfiguration::getUpdatedAt).orElse(null));
    }

    private static Optional<AiConfiguration> row(List<AiConfiguration> rows, String feature, AiFunction function) {
        return rows.stream().filter(l -> Objects.equals(l.getFeature(), feature) && l.getFunction() == function)
                .findFirst();
    }

    // Saving

    /** PUT /condominios/{id}/ia. The key is write-only: null keeps the stored one; removeKey deletes it. */
    public record Change(AiMode generalMode, AnswersChange answers, EmbeddingsChange embeddings) {
    }

    public record AnswersChange(AiMode mode, String provider, String model, String key, boolean removeKey) {

        /** Never shows the key (not even in a Spring error log). */
        @Override
        public String toString() {
            return "AnswersChange[" + mode + ", " + provider + "/" + model + ", chave "
                    + (key == null ? "não enviada" : "enviada") + ", removerChave " + removeKey + "]";
        }
    }

    public record EmbeddingsChange(AiMode mode, String provider, String model) {
    }

    /** Resolved values of a function, to compare with what is in effect and for the audit trail. */
    private record Values(AiMode mode, String provider, String model) {
    }

    /**
     * Validates everything (422 with every reason), encrypts the new key outside the transaction and saves in a single
     * transaction, with an audit trail event for each changed function. Asking for what is already in effect saves
     * nothing.
     */
    public Effective save(UUID condominiumId, Change change, String username, String authorization) {
        if (change == null || change.generalMode() == null || change.answers() == null || change.embeddings() == null
                || change.embeddings().mode() == null) {
            throw new InvalidRequestException("Informe o modo geral e a configuração do Assistente (respostas e"
                    + " embeddings, com o modo dos embeddings)");
        }
        Effective current = read(condominiumId);
        AnswersChange answersChange = change.answers();
        EmbeddingsChange embeddingsChange = change.embeddings();
        String key = answersChange.key() == null ? null : answersChange.key().strip();
        List<String> reasons = new ArrayList<>();

        if (change.generalMode() == AiMode.LOCAL) {
            reasons.add("O modo geral LOCAL ainda não está disponível nesta fase: escolha MCP_EXTERNO, API_KEY ou"
                    + " DESLIGADO.");
        }
        if (answersChange.mode() == AiMode.LOCAL) {
            reasons.add("O modo LOCAL para as respostas do Assistente ainda não está disponível nesta fase (previsto,"
                    + " sem provedor).");
        }
        AiMode effective = answersChange.mode() == null ? change.generalMode() : answersChange.mode();
        if (key != null && answersChange.removeKey()) {
            reasons.add("Informe uma chave nova ou peça para remover a chave guardada, não os dois.");
        }
        if (key != null && (key.length() < KEY_MIN_LENGTH || key.length() > KEY_MAX_LENGTH)) {
            reasons.add("A chave de API deve ter de " + KEY_MIN_LENGTH + " a " + KEY_MAX_LENGTH + " caracteres.");
        }
        if (embeddingsChange.mode() != AiMode.LOCAL && embeddingsChange.mode() != AiMode.DESLIGADO) {
            reasons.add("Para embeddings só são aceitos os modos LOCAL ou DESLIGADO nesta fase: só embeddings locais"
                    + " são permitidos, sem enviar texto para fora (Q12).");
        }

        String answersProvider = textOrNull(answersChange.provider());
        String answersModel = textOrNull(answersChange.model());
        String embeddingsProvider = embeddingsChange.mode() == AiMode.LOCAL ? textOrNull(embeddingsChange.provider()) : null;
        String embeddingsModel = embeddingsChange.mode() == AiMode.LOCAL ? textOrNull(embeddingsChange.model()) : null;
        boolean needsCatalog = effective == AiMode.API_KEY || answersProvider != null || embeddingsChange.mode() == AiMode.LOCAL
                || key != null;
        Catalog cat = needsCatalog ? catalog.read(authorization) : null; // rag down = 503, nothing saved

        // Answers: provider required in API_KEY; null model = the provider's default
        if (answersProvider == null && answersModel != null) {
            reasons.add("Informe o provedor do modelo de respostas '" + answersModel + "'.");
        }
        if (effective == AiMode.API_KEY && answersProvider == null) {
            reasons.add("No modo API_KEY, escolha o provedor das respostas no catálogo.");
        }
        if (answersProvider != null) {
            answersModel = validateProvider(cat, answersProvider, answersModel, AiFunction.RESPOSTAS, false, reasons);
        }
        boolean keyAfter = key != null || (!answersChange.removeKey() && current.answers().hasKey());
        if (effective == AiMode.API_KEY && !keyAfter) {
            reasons.add("O modo API_KEY exige a chave de API do condomínio: informe a chave.");
        }

        // Embeddings: LOCAL requires a local catalog provider; DESLIGADO has no provider or model
        if (embeddingsChange.mode() == AiMode.LOCAL) {
            if (embeddingsProvider == null) {
                reasons.add("Com embeddings LOCAL, escolha o provedor local do catálogo (ex.: "
                        + DEFAULT_EMBEDDINGS_PROVIDER + ").");
            } else {
                embeddingsModel = validateProvider(cat, embeddingsProvider, embeddingsModel, AiFunction.EMBEDDINGS,
                        true, reasons);
            }
        }
        if (!reasons.isEmpty()) {
            throw new AiConfigurationRejectedException(reasons);
        }

        byte[] encrypted = null;
        String newSuffix = null;
        if (key != null) {
            encrypted = ApiKeyCipher.encrypt(key, publicKey(cat));
            newSuffix = ApiKeyCipher.keySuffix(key);
        }

        var newAnswers = new Values(answersChange.mode(), answersProvider, answersModel);
        var newEmbeddings = new Values(embeddingsChange.mode(), embeddingsProvider, embeddingsModel);
        byte[] encryptedFinal = encrypted;
        String suffixFinal = newSuffix;
        transaction.executeWithoutResult(status -> apply(condominiumId, change.generalMode(), newAnswers,
                encryptedFinal, suffixFinal, answersChange.removeKey(), newEmbeddings, username));
        return read(condominiumId);
    }

    /**
     * Inside the transaction, with the condominium locked: compares with what is in effect, saves what changed and the
     * audit trail.
     */
    private void apply(UUID condominiumId, AiMode generalMode, Values answers, byte[] encryptedKey,
            String keySuffix, boolean removeKey, Values embeddings, String username) {
        configurations.lockForUpdate(condominiumId.toString());
        List<AiConfiguration> rows = configurations.findByCondominiumId(condominiumId);
        Instant now = Instant.now();

        // Modo geral
        Optional<AiConfiguration> general = row(rows, null, AiFunction.RESPOSTAS);
        AiMode generalBefore = general.map(AiConfiguration::getMode).orElse(DEFAULT_GENERAL_MODE);
        if (generalBefore != generalMode) {
            AiConfiguration l = general.orElseGet(() -> new AiConfiguration(condominiumId, null, AiFunction.RESPOSTAS));
            l.change(generalMode, null, null, username, now);
            configurations.save(l);
            events.save(new AiConfigurationEvent(condominiumId, null, AiFunction.RESPOSTAS, username, now,
                    generalBefore,
                    generalMode, null, null, null, null, false, null));
        }

        // Assistant answers
        Optional<AiConfiguration> ans = row(rows, FeatureService.ASSISTANT, AiFunction.RESPOSTAS);
        Values answersBefore = ans.map(l -> new Values(l.getMode(), l.getProvider(), l.getModel()))
                .orElse(new Values(null, null, null));
        boolean hadKey = ans.map(AiConfiguration::hasKey).orElse(false);
        boolean keyReplaced = encryptedKey != null || (removeKey && hadKey);
        AiMode effective = answers.mode() == null ? generalMode : answers.mode();
        if (effective == AiMode.API_KEY && encryptedKey == null && (!hadKey || removeKey)) {
            // Another Admin removed the key between validation and saving
            throw new AiConfigurationRejectedException(
                    List.of("O modo API_KEY exige a chave de API do condomínio: informe a chave."));
        }
        if (!answersBefore.equals(answers) || keyReplaced) {
            AiConfiguration l = ans.orElseGet(() -> new AiConfiguration(condominiumId, FeatureService.ASSISTANT,
                    AiFunction.RESPOSTAS));
            l.change(answers.mode(), answers.provider(), answers.model(), username, now);
            if (encryptedKey != null) {
                l.replaceKey(encryptedKey, keySuffix);
            } else if (removeKey) {
                l.replaceKey(null, null);
            }
            configurations.save(l);
            events.save(new AiConfigurationEvent(condominiumId, FeatureService.ASSISTANT, AiFunction.RESPOSTAS,
                    username, now,
                    answersBefore.mode(), answers.mode(), answersBefore.provider(), answers.provider(),
                            answersBefore.model(),
                    answers.model(), keyReplaced, encryptedKey != null ? keySuffix : null));
        }

        // Assistant embeddings
        Optional<AiConfiguration> emb = row(rows, FeatureService.ASSISTANT, AiFunction.EMBEDDINGS);
        Values embBefore = emb.map(l -> new Values(l.getMode(), l.getProvider(), l.getModel()))
                .orElse(new Values(DEFAULT_EMBEDDINGS_MODE, DEFAULT_EMBEDDINGS_PROVIDER, DEFAULT_EMBEDDINGS_MODEL));
        if (!embBefore.equals(embeddings)) {
            AiConfiguration l = emb.orElseGet(() -> new AiConfiguration(condominiumId, FeatureService.ASSISTANT,
                    AiFunction.EMBEDDINGS));
            l.change(embeddings.mode(), embeddings.provider(), embeddings.model(), username, now);
            configurations.save(l);
            events.save(new AiConfigurationEvent(condominiumId, FeatureService.ASSISTANT, AiFunction.EMBEDDINGS,
                    username,
                    now, embBefore.mode(), embeddings.mode(), embBefore.provider(), embeddings.provider(),
                    embBefore.model(), embeddings.model(), false, null));
        }
        log.info("Configuração de IA do condomínio {} gravada por {} (modo geral {}, respostas {}, embeddings {}{})",
                condominiumId, username, generalMode, answers.mode() == null ? "herda" : answers.mode(),
                embeddings.mode(), encryptedKey != null ? ", chave trocada" : removeKey ? ", chave removida" : "");
    }

    /** Checks provider and model in the catalog; returns the resolved model (the provider's default when null). */
    private static String validateProvider(Catalog cat, String provider, String model, AiFunction usage,
            boolean requiresLocal, List<String> reasons) {
        Optional<AiProvider> p = cat.provider(provider);
        if (p.isEmpty()) {
            reasons.add("O provedor '" + provider + "' não está no catálogo.");
            return model;
        }
        if (p.get().function() != usage) {
            reasons.add("O provedor '" + provider + "' não é de " + (usage == AiFunction.RESPOSTAS ? "respostas"
                    : "embeddings") + ".");
            return model;
        }
        if (requiresLocal && !p.get().local()) {
            reasons.add("O provedor '" + provider + "' não é local: para embeddings só é aceito provedor local, sem"
                    + " enviar texto para fora (Q12).");
            return model;
        }
        if (model == null) {
            Optional<AiModel> defaultModel = p.get().defaultModel();
            if (defaultModel.isEmpty()) {
                reasons.add("O provedor '" + provider + "' não tem modelos no catálogo.");
                return null;
            }
            return defaultModel.get().id();
        }
        if (p.get().model(model).isEmpty()) {
            reasons.add("O modelo '" + model + "' não está no catálogo do provedor '" + provider + "'.");
        }
        return model;
    }

    private static PublicKey publicKey(Catalog cat) {
        if (cat == null || cat.publicKeyPem() == null || cat.publicKeyPem().isBlank()) {
            throw new AiUnavailableException(NO_PUBLIC_KEY);
        }
        try {
            return ApiKeyCipher.publicKey(cat.publicKeyPem());
        } catch (IllegalArgumentException error) {
            log.error("Chave pública do rag recusada: {}", error.getMessage());
            throw new AiUnavailableException(NO_PUBLIC_KEY);
        }
    }

    private static String textOrNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
