package br.com.condominioauditoria.backend.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.backend.grpc.ClienteAssistente;
import br.com.condominioauditoria.backend.modulo.CustoUso.PrecoModelo;
import io.grpc.Status;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Catálogo de provedores lido do rag, com cache curto, conversão e preços exatos. */
class CatalogoIaTest {

    private final ClienteAssistente rag = mock(ClienteAssistente.class);
    private final AtomicReference<Instant> agora = new AtomicReference<>(Instant.parse("2026-10-05T12:00:00Z"));
    private final Clock relogio = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora.get();
        }
    };
    private final CatalogoIa catalogo = new CatalogoIa(rag, Duration.ofMinutes(5), relogio);

    @Test
    void converteOCatalogoNaOrdemDoRag() {
        when(rag.listarProvedores("Bearer t")).thenReturn(CatalogoTeste.resposta("PEM"));

        var lido = catalogo.ler("Bearer t");

        assertThat(lido.provedores()).extracting(CatalogoIa.ProvedorIa::codigo)
                .containsExactly("anthropic", "ollama-local", "voyage");
        var anthropic = lido.provedor("anthropic").orElseThrow();
        assertThat(anthropic.uso()).isEqualTo(FuncaoIa.RESPOSTAS);
        assertThat(anthropic.precisaChave()).isTrue();
        assertThat(anthropic.dimensao()).isNull();
        assertThat(anthropic.modeloPadrao().orElseThrow().id()).isEqualTo("claude-sonnet-5-5");
        var ollama = lido.provedor("ollama-local").orElseThrow();
        assertThat(ollama.local()).isTrue();
        assertThat(ollama.dimensao()).isEqualTo(1024);
        assertThat(lido.chavePublicaPem()).isEqualTo("PEM");
        assertThat(lido.precos()).containsEntry("anthropic/claude-haiku-4-5",
                new PrecoModelo(new BigDecimal("1.00"), new BigDecimal("5.00")));
    }

    @Test
    void guardaPorAlgunsMinutosEDepoisLeDeNovo() {
        when(rag.listarProvedores("Bearer t")).thenReturn(CatalogoTeste.resposta("PEM"));

        catalogo.ler("Bearer t");
        agora.set(agora.get().plusSeconds(299));
        catalogo.ler("Bearer t");
        verify(rag, times(1)).listarProvedores("Bearer t");

        agora.set(agora.get().plusSeconds(2));
        catalogo.ler("Bearer t");
        verify(rag, times(2)).listarProvedores("Bearer t");
    }

    @Test
    void ragForaDoArEhIndisponivelEPrecosVazios() {
        when(rag.listarProvedores("Bearer t")).thenThrow(Status.UNAVAILABLE.asRuntimeException());

        assertThatThrownBy(() -> catalogo.ler("Bearer t")).isInstanceOf(IaIndisponivelException.class)
                .hasMessageContaining("rag não respondeu");
        assertThat(catalogo.precos("Bearer t")).isEmpty();
    }

    @Test
    void precoForaDoFormatoFicaSemPreco() {
        assertThat(CatalogoIa.preco("2.00")).isEqualByComparingTo("2");
        assertThat(CatalogoIa.preco("")).isEqualByComparingTo("0");
        assertThat(CatalogoIa.preco("2,00")).isNull();
        assertThat(CatalogoIa.preco("1e3")).isNull();
    }
}
