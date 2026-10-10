package br.com.condominioauditoria.api.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.dto.response.feature.AssistantContextResponse;
import br.com.condominioauditoria.api.exception.AiConfigurationRejectedException;
import br.com.condominioauditoria.api.exception.AiUnavailableException;
import br.com.condominioauditoria.api.exception.InvalidRequestException;
import br.com.condominioauditoria.api.grpc.client.AssistantClient;
import br.com.condominioauditoria.api.model.ai.AiConfiguration;
import br.com.condominioauditoria.api.model.ai.AiConfigurationEvent;
import br.com.condominioauditoria.api.model.enums.AiFunction;
import br.com.condominioauditoria.api.model.enums.AiMode;
import br.com.condominioauditoria.api.repository.ai.AiConfigurationEventRepository;
import br.com.condominioauditoria.api.repository.ai.AiConfigurationRepository;
import br.com.condominioauditoria.api.security.TestKeyPair;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService.AnswersChange;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService.Change;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService.EmbeddingsChange;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import io.grpc.Status;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/**
 * AI configuration (RF-09.1, 09.2, 09.6): defaults without a row, inheritance of the general mode, the contract's 422
 * rejections, an encrypted key only the rag decrypts, audit trail with previous and new and never the key.
 */
class AiConfigurationServiceTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();
    private static final String KEY = "sk-ant-api03-ChaveDeTesteNaoReal-x9Qa";
    private static final EmbeddingsChange DEFAULT_EMBEDDINGS = new EmbeddingsChange(AiMode.LOCAL, "ollama-local", null);

    private final AiConfigurationRepository configurations = mock(AiConfigurationRepository.class);
    private final AiConfigurationEventRepository events = mock(AiConfigurationEventRepository.class);
    private final AssistantClient rag = mock(AssistantClient.class);
    private final FeatureService features = mock(FeatureService.class);
    private final List<AiConfiguration> rows = new ArrayList<>();
    private final List<AiConfigurationEvent> trail = new ArrayList<>();
    private AiConfigurationService service;

    @BeforeEach
    void setUp() {
        when(configurations.findByCondominiumId(any())).thenAnswer(i -> rows.stream()
                .filter(l -> l.getCondominiumId().equals(i.getArgument(0))).toList());
        when(configurations.save(any())).thenAnswer(i -> {
            AiConfiguration c = i.getArgument(0);
            rows.removeIf(l -> l.getId().equals(c.getId()));
            rows.add(c);
            return c;
        });
        when(events.save(any())).thenAnswer(i -> {
            trail.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(rag.listProviders(anyString()))
                .thenReturn(TestCatalog.response(TestKeyPair.publicPem(TestKeyPair.pair())));
        service = new AiConfigurationService(configurations, events, new AiCatalog(rag, Duration.ZERO,
                java.time.Clock.systemUTC()), features, TransactionOperations.withoutTransaction());
    }

    @Test
    void withoutRowsDefaultsApply() {
        var e = service.read(CONDOMINIUM);

        assertThat(e.generalMode()).isEqualTo(AiMode.EXTERNAL_MCP);
        assertThat(e.answers().mode()).isNull();
        assertThat(e.answers().effectiveMode()).isEqualTo(AiMode.EXTERNAL_MCP);
        assertThat(e.answers().hasKey()).isFalse();
        assertThat(e.embeddings()).isEqualTo(new AiConfigurationService.Embeddings(AiMode.LOCAL, "ollama-local",
                "bge-m3"));
        assertThat(e.updatedBy()).isNull();
        assertThat(e.updatedAt()).isNull();
    }

    @Test
    void requestingDefaultsSavesNothingAndDoesNotCallRag() {
        service.save(CONDOMINIUM, new Change(AiMode.EXTERNAL_MCP, new AnswersChange(null, null, null, null, false),
                new EmbeddingsChange(AiMode.OFF, null, null)), "admin", "Bearer t");
        trail.clear();
        rows.clear();

        service.save(CONDOMINIUM, new Change(AiMode.EXTERNAL_MCP, new AnswersChange(null, null, null, null, false),
                DEFAULT_EMBEDDINGS), "admin", "Bearer t");

        assertThat(rows).isEmpty();
        assertThat(trail).isEmpty();
    }

    @Test
    void offWithoutProviderDoesNotNeedTheCatalog() {
        service.save(CONDOMINIUM, new Change(AiMode.OFF, new AnswersChange(null, null, null, null, false),
                new EmbeddingsChange(AiMode.OFF, "ignorado", "ignorado")), "admin", "Bearer t");

        verify(rag, never()).listProviders(anyString());
        var e = service.read(CONDOMINIUM);
        assertThat(e.generalMode()).isEqualTo(AiMode.OFF);
        assertThat(e.answers().effectiveMode()).isEqualTo(AiMode.OFF);
        assertThat(e.embeddings()).isEqualTo(new AiConfigurationService.Embeddings(AiMode.OFF, null, null));
        assertThat(e.updatedBy()).isEqualTo("admin");
    }

    @Test
    void apiKeyEncryptsWithRagPublicKeyAndKeepsLastFour() throws Exception {
        var e = service.save(CONDOMINIUM, new Change(AiMode.EXTERNAL_MCP,
                new AnswersChange(AiMode.API_KEY, "anthropic", null, "  " + KEY + " ", false), DEFAULT_EMBEDDINGS),
                "admin", "Bearer t");

        assertThat(e.answers().effectiveMode()).isEqualTo(AiMode.API_KEY);
        assertThat(e.answers().provider()).isEqualTo("anthropic");
        assertThat(e.answers().model()).isEqualTo("claude-sonnet-5-5"); // provider's default
        assertThat(e.answers().hasKey()).isTrue();
        assertThat(e.answers().keySuffix()).isEqualTo("x9Qa");
        assertThat(e.answers().chatAvailable()).isTrue();
        assertThat(TestKeyPair.decrypt(e.answers().encryptedKey(), TestKeyPair.pair().getPrivate()))
                .isEqualTo(KEY);
        assertThat(e.toString()).doesNotContain(KEY);

        assertThat(trail).singleElement().satisfies(ev -> {
            assertThat(ev.getFeature()).isEqualTo(FeatureService.ASSISTANT);
            assertThat(ev.getFunction()).isEqualTo(AiFunction.ANSWERS);
            assertThat(ev.getUsername()).isEqualTo("admin");
            assertThat(ev.getPreviousMode()).isNull();
            assertThat(ev.getNewMode()).isEqualTo(AiMode.API_KEY);
            assertThat(ev.getNewProvider()).isEqualTo("anthropic");
            assertThat(ev.getNewModel()).isEqualTo("claude-sonnet-5-5");
            assertThat(ev.isKeyReplaced()).isTrue();
            assertThat(ev.getKeySuffix()).isEqualTo("x9Qa");
        });
        assertThat(trailTexts()).noneMatch(t -> t.contains(KEY.substring(0, 10)));
    }

    @Test
    void assistantWithoutOwnModeInheritsGeneralAndFollowsIt() {
        service.save(CONDOMINIUM, new Change(AiMode.API_KEY,
                new AnswersChange(null, "anthropic", "claude-haiku-4-5", KEY, false), DEFAULT_EMBEDDINGS), "admin",
                "Bearer t");
        assertThat(service.read(CONDOMINIUM).answers().effectiveMode()).isEqualTo(AiMode.API_KEY);

        service.save(CONDOMINIUM, new Change(AiMode.EXTERNAL_MCP,
                new AnswersChange(null, "anthropic", "claude-haiku-4-5", null, false), DEFAULT_EMBEDDINGS), "admin",
                "Bearer t");

        var e = service.read(CONDOMINIUM);
        assertThat(e.answers().mode()).isNull();
        assertThat(e.answers().effectiveMode()).isEqualTo(AiMode.EXTERNAL_MCP);
        assertThat(e.answers().hasKey()).isTrue(); // a chave fica guardada
        assertThat(e.answers().chatAvailable()).isFalse();
        assertThat(trail).last().satisfies(ev -> {
            assertThat(ev.getFeature()).isNull(); // general mode event
            assertThat(ev.getPreviousMode()).isEqualTo(AiMode.API_KEY);
            assertThat(ev.getNewMode()).isEqualTo(AiMode.EXTERNAL_MCP);
            assertThat(ev.isKeyReplaced()).isFalse();
        });
    }

    @Test
    void keepsKeyWithoutResendingAndRemovesWithRemoveKey() {
        service.save(CONDOMINIUM, new Change(AiMode.EXTERNAL_MCP,
                new AnswersChange(AiMode.API_KEY, "anthropic", null, KEY, false), DEFAULT_EMBEDDINGS), "admin",
                        "Bearer t");
        byte[] stored = service.read(CONDOMINIUM).answers().encryptedKey();

        service.save(CONDOMINIUM, new Change(AiMode.EXTERNAL_MCP,
                new AnswersChange(AiMode.API_KEY, "anthropic", "claude-haiku-4-5", null, false), DEFAULT_EMBEDDINGS),
                        "outro",
                "Bearer t");
        assertThat(service.read(CONDOMINIUM).answers().encryptedKey()).isEqualTo(stored);
        assertThat(trail).last().satisfies(ev -> {
            assertThat(ev.getPreviousModel()).isEqualTo("claude-sonnet-5-5");
            assertThat(ev.getNewModel()).isEqualTo("claude-haiku-4-5");
            assertThat(ev.isKeyReplaced()).isFalse();
            assertThat(ev.getKeySuffix()).isNull();
        });

        service.save(CONDOMINIUM, new Change(AiMode.EXTERNAL_MCP,
                new AnswersChange(AiMode.OFF, "anthropic", "claude-haiku-4-5", null, true), DEFAULT_EMBEDDINGS),
                        "admin",
                "Bearer t");
        var e = service.read(CONDOMINIUM);
        assertThat(e.answers().hasKey()).isFalse();
        assertThat(e.answers().keySuffix()).isNull();
        assertThat(trail).last().satisfies(ev -> {
            assertThat(ev.isKeyReplaced()).isTrue();
            assertThat(ev.getKeySuffix()).isNull(); // removida
        });
    }

    @Test
    void contractRejectionsComeAllAtOnce() {
        assertThatThrownBy(() -> service.save(CONDOMINIUM, new Change(AiMode.EXTERNAL_MCP,
                new AnswersChange(AiMode.LOCAL, null, null, KEY, true),
                new EmbeddingsChange(AiMode.API_KEY, "voyage", null)), "admin", "Bearer t"))
                .isInstanceOfSatisfying(AiConfigurationRejectedException.class, e -> assertThat(e.reasons())
                        .anyMatch(m -> m.contains("LOCAL para as respostas"))
                        .anyMatch(m -> m.contains("não os dois"))
                        .anyMatch(m -> m.contains("só embeddings locais")));
        assertThat(rows).isEmpty();
        assertThat(trail).isEmpty();
    }

    @Test
    void apiKeyWithoutKeyIsRejected() {
        assertThatThrownBy(() -> service.save(CONDOMINIUM, new Change(AiMode.API_KEY,
                new AnswersChange(null, "anthropic", null, null, false), DEFAULT_EMBEDDINGS), "admin", "Bearer t"))
                .isInstanceOfSatisfying(AiConfigurationRejectedException.class, e -> assertThat(e.reasons())
                        .containsExactly("O modo API_KEY exige a chave de API do condomínio: informe a chave."));
    }

    @Test
    void apiKeyWithoutProviderOrOutsideCatalogIsRejected() {
        assertThatThrownBy(() -> service.save(CONDOMINIUM, new Change(AiMode.EXTERNAL_MCP,
                new AnswersChange(AiMode.API_KEY, null, null, KEY, false), DEFAULT_EMBEDDINGS), "admin", "Bearer t"))
                .isInstanceOfSatisfying(AiConfigurationRejectedException.class,
                        e -> assertThat(e.reasons()).anyMatch(m -> m.contains("escolha o provedor das respostas")));
        assertThatThrownBy(() -> service.save(CONDOMINIUM, new Change(AiMode.EXTERNAL_MCP,
                new AnswersChange(AiMode.API_KEY, "openai", null, KEY, false), DEFAULT_EMBEDDINGS), "admin",
                        "Bearer t"))
                .isInstanceOfSatisfying(AiConfigurationRejectedException.class,
                        e -> assertThat(e.reasons()).containsExactly("O provedor 'openai' não está no catálogo."));
        assertThatThrownBy(() -> service.save(CONDOMINIUM, new Change(AiMode.EXTERNAL_MCP,
                new AnswersChange(AiMode.API_KEY, "anthropic", "gpt-9", KEY, false), DEFAULT_EMBEDDINGS), "admin",
                "Bearer t"))
                .isInstanceOfSatisfying(AiConfigurationRejectedException.class, e -> assertThat(e.reasons())
                        .containsExactly("O modelo 'gpt-9' não está no catálogo do provedor 'anthropic'."));
        assertThatThrownBy(() -> service.save(CONDOMINIUM, new Change(AiMode.EXTERNAL_MCP,
                new AnswersChange(AiMode.API_KEY, "ollama-local", null, KEY, false), DEFAULT_EMBEDDINGS), "admin",
                "Bearer t"))
                .isInstanceOfSatisfying(AiConfigurationRejectedException.class,
                        e -> assertThat(e.reasons()).containsExactly("O provedor 'ollama-local' não é de respostas."));
    }

    /** RF-09.6: OFF mode with external-provider embeddings = rejected; with a local provider = accepted. */
    @Test
    void embeddingsOnlyWithLocalProvider() {
        assertThatThrownBy(() -> service.save(CONDOMINIUM, new Change(AiMode.OFF,
                new AnswersChange(null, null, null, null, false), new EmbeddingsChange(AiMode.LOCAL, "voyage", null)),
                "admin", "Bearer t"))
                .isInstanceOfSatisfying(AiConfigurationRejectedException.class, e -> assertThat(e.reasons())
                        .containsExactly("O provedor 'voyage' não é local: para embeddings só é aceito provedor local,"
                                + " sem enviar texto para fora (Q12)."));

        var e = service.save(CONDOMINIUM, new Change(AiMode.OFF, new AnswersChange(null, null, null, null,
                false), new EmbeddingsChange(AiMode.LOCAL, "ollama-local", "bge-m3")), "admin", "Bearer t");
        assertThat(e.embeddings().mode()).isEqualTo(AiMode.LOCAL);
    }

    @Test
    void localGeneralModeAndKeyOutOfLengthAreRejected() {
        assertThatThrownBy(() -> service.save(CONDOMINIUM, new Change(AiMode.LOCAL,
                new AnswersChange(AiMode.OFF, null, null, "curta", false), DEFAULT_EMBEDDINGS), "admin",
                        "Bearer t"))
                .isInstanceOfSatisfying(AiConfigurationRejectedException.class, e -> assertThat(e.reasons())
                        .anyMatch(m -> m.startsWith("O modo geral LOCAL"))
                        .anyMatch(m -> m.contains("de 8 a 500 caracteres"))
                        .noneMatch(m -> m.contains("curta")));
    }

    @Test
    void ragDownOrWithoutPublicKeySavesNothing() {
        doThrow(Status.UNAVAILABLE.asRuntimeException()).when(rag).listProviders(anyString());
        assertThatThrownBy(() -> service.save(CONDOMINIUM, new Change(AiMode.EXTERNAL_MCP,
                new AnswersChange(AiMode.API_KEY, "anthropic", null, KEY, false), DEFAULT_EMBEDDINGS), "admin",
                        "Bearer t"))
                .isInstanceOf(AiUnavailableException.class);

        doReturn(TestCatalog.response("")).when(rag).listProviders(anyString());
        assertThatThrownBy(() -> service.save(CONDOMINIUM, new Change(AiMode.EXTERNAL_MCP,
                new AnswersChange(AiMode.API_KEY, "anthropic", null, KEY, false), DEFAULT_EMBEDDINGS), "admin",
                        "Bearer t"))
                .isInstanceOf(AiUnavailableException.class).hasMessageContaining("sem chave pública");
        assertThat(rows).isEmpty();
        assertThat(trail).isEmpty();
    }

    @Test
    void incompleteRequestIs400() {
        assertThatThrownBy(() -> service.save(CONDOMINIUM, new Change(null, null, DEFAULT_EMBEDDINGS), "admin",
                "Bearer t"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void contextNullWithFeatureOffAndChatOnlyWithApiKeyAndKey() {
        when(features.isEnabled(CONDOMINIUM, FeatureService.ASSISTANT)).thenReturn(false);
        assertThat(service.assistantContext(CONDOMINIUM)).isNull();

        when(features.isEnabled(CONDOMINIUM, FeatureService.ASSISTANT)).thenReturn(true);
        assertThat(service.assistantContext(CONDOMINIUM)).isEqualTo(
                new AssistantContextResponse(AiMode.EXTERNAL_MCP, AiMode.LOCAL, false));

        service.save(CONDOMINIUM, new Change(AiMode.EXTERNAL_MCP,
                new AnswersChange(AiMode.API_KEY, "anthropic", null, KEY, false), DEFAULT_EMBEDDINGS), "admin",
                        "Bearer t");
        assertThat(service.assistantContext(CONDOMINIUM)).isEqualTo(
                new AssistantContextResponse(AiMode.API_KEY, AiMode.LOCAL, true));
    }

    @Test
    void requestNeverShowsKeyInToString() {
        assertThat(new AnswersChange(AiMode.API_KEY, "anthropic", null, KEY, false).toString())
                .doesNotContain(KEY).contains("chave enviada");
    }

    private List<String> trailTexts() {
        List<String> texts = new ArrayList<>();
        for (AiConfigurationEvent ev : trail) {
            for (Field f : AiConfigurationEvent.class.getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    Object v = f.get(ev);
                    if (v != null) {
                        texts.add(v.toString());
                    }
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return texts;
    }
}
