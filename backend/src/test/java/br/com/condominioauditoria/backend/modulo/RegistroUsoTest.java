package br.com.condominioauditoria.backend.modulo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.backend.modulo.UsoModuloRepository.LinhaMensal;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Registro de uso (RF-09.7): o que é gravado, o que nunca é gravado e o resumo por função e por mês. */
class RegistroUsoTest {

    private static final UUID CONDOMINIO = UUID.randomUUID();

    private final UsoModuloRepository usos = mock(UsoModuloRepository.class);
    private final RegistroUso registro = new RegistroUso(usos);

    @Test
    void indexacaoComVetoresEhLocalComOModelo() {
        registro.indexacao(CONDOMINIO, 12, "bge-m3");

        UsoModulo uso = gravado();
        assertThat(uso.getModulo()).isEqualTo(Modulos.ASSISTENTE);
        assertThat(uso.getFuncao()).isEqualTo(FuncaoUso.INDEXACAO);
        assertThat(uso.getModo()).isEqualTo(ModoIa.LOCAL);
        assertThat(uso.getModelo()).isEqualTo("bge-m3");
        assertThat(uso.getArquivos()).isEqualTo(1);
        assertThat(uso.getPaginas()).isEqualTo(12);
        assertThat(uso.getUsuario()).isNull();
        assertThat(uso.getTokensEntrada()).isNull();
        assertThat(uso.getTokensSaida()).isNull();
        assertThat(uso.getVersaoPrompt()).isNull();
    }

    @Test
    void indexacaoSemVetoresEhDesligado() {
        registro.indexacao(CONDOMINIO, 3, null);

        UsoModulo uso = gravado();
        assertThat(uso.getModo()).isEqualTo(ModoIa.DESLIGADO);
        assertThat(uso.getModelo()).isNull();
    }

    @Test
    void chamadaMcpGuardaUsuarioEModoSemTokens() {
        registro.chamadaMcp(CONDOMINIO, "conselheiro", false);

        UsoModulo uso = gravado();
        assertThat(uso.getFuncao()).isEqualTo(FuncaoUso.CHAMADA_MCP);
        assertThat(uso.getUsuario()).isEqualTo("conselheiro");
        assertThat(uso.getModo()).isEqualTo(ModoIa.DESLIGADO);
        assertThat(uso.getTokensEntrada()).isNull();
        assertThat(uso.getArquivos()).isNull();
    }

    @Test
    void registroNaoTemCampoDeTextoNemDeChave() {
        assertThat(Arrays.stream(UsoModulo.class.getDeclaredFields()).map(Field::getName))
                .noneMatch(nome -> nome.toLowerCase().matches(".*(texto|chave|pergunta|conteudo|trecho|key).*"));
    }

    @Test
    void resumoSomaOsMesesPorFuncaoEUsaOFusoDeBrasilia() {
        when(usos.totaisPorMes(any(), any(), any())).thenReturn(List.of(
                linha("2026-10", "chamada_mcp", 7, 0, 0),
                linha("2026-10", "indexacao", 40, 40, 380),
                linha("2026-11", "chamada_mcp", 5, 0, 0)));

        var resumo = registro.resumo(CONDOMINIO, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30));

        assertThat(resumo.porMes()).hasSize(3);
        assertThat(resumo.porFuncao()).containsExactly(
                new TotalUso(null, Modulos.ASSISTENTE, FuncaoUso.CHAMADA_MCP, 12, 0, 0, 0, 0),
                new TotalUso(null, Modulos.ASSISTENTE, FuncaoUso.INDEXACAO, 40, 0, 0, 40, 380));
        var de = ArgumentCaptor.forClass(Instant.class);
        var ate = ArgumentCaptor.forClass(Instant.class);
        verify(usos).totaisPorMes(org.mockito.ArgumentMatchers.eq(CONDOMINIO), de.capture(), ate.capture());
        assertThat(de.getValue()).isEqualTo(Instant.parse("2026-10-01T03:00:00Z"));
        assertThat(ate.getValue()).isEqualTo(Instant.parse("2026-12-01T03:00:00Z")); // fim incluído
    }

    @Test
    void periodoInvertidoOuIncompletoEhRecusado() {
        assertThatThrownBy(() -> registro.resumo(CONDOMINIO, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(PedidoInvalidoException.class);
        assertThatThrownBy(() -> registro.resumo(CONDOMINIO, null, LocalDate.of(2026, 10, 1)))
                .isInstanceOf(PedidoInvalidoException.class);
        verifyNoInteractions(usos);
    }

    private UsoModulo gravado() {
        var uso = ArgumentCaptor.forClass(UsoModulo.class);
        verify(usos).save(uso.capture());
        assertThat(uso.getValue().getCondominioId()).isEqualTo(CONDOMINIO);
        assertThat(uso.getValue().getQuando()).isNotNull();
        return uso.getValue();
    }

    private static LinhaMensal linha(String mes, String funcao, long quantidade, long arquivos, long paginas) {
        return new LinhaMensal() {
            public String getMes() {
                return mes;
            }

            public String getModulo() {
                return Modulos.ASSISTENTE;
            }

            public String getFuncao() {
                return funcao;
            }

            public Long getQuantidade() {
                return quantidade;
            }

            public Long getTokensEntrada() {
                return 0L;
            }

            public Long getTokensSaida() {
                return 0L;
            }

            public Long getArquivos() {
                return arquivos;
            }

            public Long getPaginas() {
                return paginas;
            }
        };
    }
}
