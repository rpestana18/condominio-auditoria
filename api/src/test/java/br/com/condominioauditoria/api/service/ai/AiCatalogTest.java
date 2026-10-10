package br.com.condominioauditoria.api.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.exception.AiUnavailableException;
import br.com.condominioauditoria.api.grpc.client.AssistantClient;
import br.com.condominioauditoria.api.model.enums.AiFunction;
import br.com.condominioauditoria.api.service.calculator.UsageCostCalculator.ModelPrice;
import io.grpc.Status;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Provider catalog read from the rag, with a short cache, conversion and exact prices. */
class AiCatalogTest {

    private final AssistantClient rag = mock(AssistantClient.class);
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-05T12:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };
    private final AiCatalog catalog = new AiCatalog(rag, Duration.ofMinutes(5), clock);

    @Test
    void convertsTheCatalogInRagOrder() {
        when(rag.listProviders("Bearer t")).thenReturn(TestCatalog.response("PEM"));

        var loaded = catalog.read("Bearer t");

        assertThat(loaded.providers()).extracting(AiCatalog.AiProvider::code)
                .containsExactly("anthropic", "ollama-local", "voyage");
        var anthropic = loaded.provider("anthropic").orElseThrow();
        assertThat(anthropic.function()).isEqualTo(AiFunction.ANSWERS);
        assertThat(anthropic.requiresKey()).isTrue();
        assertThat(anthropic.dimension()).isNull();
        assertThat(anthropic.defaultModel().orElseThrow().id()).isEqualTo("claude-sonnet-5-5");
        var ollama = loaded.provider("ollama-local").orElseThrow();
        assertThat(ollama.local()).isTrue();
        assertThat(ollama.dimension()).isEqualTo(1024);
        assertThat(loaded.publicKeyPem()).isEqualTo("PEM");
        assertThat(loaded.prices()).containsEntry("anthropic/claude-haiku-4-5",
                new ModelPrice(new BigDecimal("1.00"), new BigDecimal("5.00")));
    }

    @Test
    void cachesForAFewMinutesThenReadsAgain() {
        when(rag.listProviders("Bearer t")).thenReturn(TestCatalog.response("PEM"));

        catalog.read("Bearer t");
        now.set(now.get().plusSeconds(299));
        catalog.read("Bearer t");
        verify(rag, times(1)).listProviders("Bearer t");

        now.set(now.get().plusSeconds(2));
        catalog.read("Bearer t");
        verify(rag, times(2)).listProviders("Bearer t");
    }

    @Test
    void ragDownIsUnavailableAndPricesAreEmpty() {
        when(rag.listProviders("Bearer t")).thenThrow(Status.UNAVAILABLE.asRuntimeException());

        assertThatThrownBy(() -> catalog.read("Bearer t")).isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("rag não respondeu");
        assertThat(catalog.prices("Bearer t")).isEmpty();
    }

    @Test
    void priceOutOfFormatHasNoPrice() {
        assertThat(AiCatalog.price("2.00")).isEqualByComparingTo("2");
        assertThat(AiCatalog.price("")).isEqualByComparingTo("0");
        assertThat(AiCatalog.price("2,00")).isNull();
        assertThat(AiCatalog.price("1e3")).isNull();
    }
}
