package br.com.condominioauditoria.api.service.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.exception.InvalidRequestException;
import br.com.condominioauditoria.api.model.enums.AiMode;
import br.com.condominioauditoria.api.model.enums.UsageFunction;
import br.com.condominioauditoria.api.model.usage.FeatureUsage;
import br.com.condominioauditoria.api.model.usage.UsageTotal;
import br.com.condominioauditoria.api.repository.usage.FeatureUsageRepository;
import br.com.condominioauditoria.api.repository.usage.FeatureUsageRepository.MonthlyRow;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Usage records (RF-09.7): what is saved, what is never saved and the summary by function and by month. */
class UsageServiceTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();

    private final FeatureUsageRepository usages = mock(FeatureUsageRepository.class);
    private final UsageService usage = new UsageService(usages);

    @Test
    void indexingWithVectorsIsLocalWithTheModel() {
        usage.recordIndexing(CONDOMINIUM, 12, "bge-m3");

        FeatureUsage usage = saved();
        assertThat(usage.getFeature()).isEqualTo(FeatureService.ASSISTANT);
        assertThat(usage.getFunction()).isEqualTo(UsageFunction.INDEXING);
        assertThat(usage.getMode()).isEqualTo(AiMode.LOCAL);
        assertThat(usage.getModel()).isEqualTo("bge-m3");
        assertThat(usage.getFiles()).isEqualTo(1);
        assertThat(usage.getPages()).isEqualTo(12);
        assertThat(usage.getUsername()).isNull();
        assertThat(usage.getInputTokens()).isNull();
        assertThat(usage.getOutputTokens()).isNull();
        assertThat(usage.getPromptVersion()).isNull();
    }

    @Test
    void indexingWithoutVectorsIsOff() {
        usage.recordIndexing(CONDOMINIUM, 3, null);

        FeatureUsage usage = saved();
        assertThat(usage.getMode()).isEqualTo(AiMode.OFF);
        assertThat(usage.getModel()).isNull();
    }

    @Test
    void mcpCallKeepsUserAndModeWithoutTokens() {
        usage.recordMcpCall(CONDOMINIUM, "conselheiro", false);

        FeatureUsage usage = saved();
        assertThat(usage.getFunction()).isEqualTo(UsageFunction.MCP_CALL);
        assertThat(usage.getUsername()).isEqualTo("conselheiro");
        assertThat(usage.getMode()).isEqualTo(AiMode.OFF);
        assertThat(usage.getInputTokens()).isNull();
        assertThat(usage.getFiles()).isNull();
    }

    @Test
    void recordHasNoTextOrKeyField() {
        assertThat(Arrays.stream(FeatureUsage.class.getDeclaredFields()).map(Field::getName))
                .noneMatch(name -> name.toLowerCase()
                        .matches(".*(texto|chave|pergunta|conteudo|trecho|text|key|question|content|chunk).*"));
    }

    @Test
    void summaryAddsMonthsByFunctionAndUsesBrasiliaTimeZone() {
        when(usages.monthlyTotals(any(), any(), any())).thenReturn(List.of(
                row("2026-10", "mcp_call", 7, 0, 0), row("2026-10", "indexing", 40, 40, 380),
                row("2026-11", "mcp_call", 5, 0, 0)));

        var summary = usage.summary(CONDOMINIUM, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30));

        assertThat(summary.byMonth()).hasSize(3);
        assertThat(summary.byFunction()).containsExactly(
                new UsageTotal(null, FeatureService.ASSISTANT, UsageFunction.MCP_CALL, 12, 0, 0, 0, 0),
                new UsageTotal(null, FeatureService.ASSISTANT, UsageFunction.INDEXING, 40, 0, 0, 40, 380));
        var from = ArgumentCaptor.forClass(Instant.class);
        var to = ArgumentCaptor.forClass(Instant.class);
        verify(usages).monthlyTotals(org.mockito.ArgumentMatchers.eq(CONDOMINIUM), from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(Instant.parse("2026-10-01T03:00:00Z"));
        assertThat(to.getValue()).isEqualTo(Instant.parse("2026-12-01T03:00:00Z")); // end included
    }

    @Test
    void invertedOrIncompletePeriodIsRejected() {
        assertThatThrownBy(() -> usage.summary(CONDOMINIUM, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> usage.summary(CONDOMINIUM, null, LocalDate.of(2026, 10, 1)))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(usages);
    }

    @Test
    void questionKeepsTokensProviderModelAndPromptVersionInApiKey() {
        usage.recordQuestion(CONDOMINIUM, "conselheiro", "anthropic", "claude-sonnet-5-5", 1200, 340, "2026-10-05.1");

        FeatureUsage usage = saved();
        assertThat(usage.getFunction()).isEqualTo(UsageFunction.QUESTION);
        assertThat(usage.getMode()).isEqualTo(AiMode.API_KEY);
        assertThat(usage.getUsername()).isEqualTo("conselheiro");
        assertThat(usage.getProvider()).isEqualTo("anthropic");
        assertThat(usage.getModel()).isEqualTo("claude-sonnet-5-5");
        assertThat(usage.getInputTokens()).isEqualTo(1200);
        assertThat(usage.getOutputTokens()).isEqualTo(340);
        assertThat(usage.getPromptVersion()).isEqualTo("2026-10-05.1");
    }

    @Test
    void searchFromTheScreenIsOffWithoutTokens() {
        usage.recordDocumentSearch(CONDOMINIUM, "conselheiro");

        FeatureUsage usage = saved();
        assertThat(usage.getFunction()).isEqualTo(UsageFunction.DOCUMENT_SEARCH);
        assertThat(usage.getMode()).isEqualTo(AiMode.OFF);
        assertThat(usage.getInputTokens()).isNull();
        assertThat(usage.getModel()).isNull();
    }

    private FeatureUsage saved() {
        var usage = ArgumentCaptor.forClass(FeatureUsage.class);
        verify(usages).save(usage.capture());
        assertThat(usage.getValue().getCondominiumId()).isEqualTo(CONDOMINIUM);
        assertThat(usage.getValue().getOccurredAt()).isNotNull();
        return usage.getValue();
    }

    private static MonthlyRow row(String month, String function, long count, long files, long pages) {
        return new MonthlyRow() {
            public String getMonth() {
                return month;
            }

            public String getFeature() {
                return FeatureService.ASSISTANT;
            }

            public String getFunction() {
                return function;
            }

            public Long getCount() {
                return count;
            }

            public Long getInputTokens() {
                return 0L;
            }

            public Long getOutputTokens() {
                return 0L;
            }

            public Long getFiles() {
                return files;
            }

            public Long getPages() {
                return pages;
            }
        };
    }
}
