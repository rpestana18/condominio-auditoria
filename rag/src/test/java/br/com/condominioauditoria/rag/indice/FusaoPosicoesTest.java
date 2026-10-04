package br.com.condominioauditoria.rag.indice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Fusão de posições (RRF, k = 60): só a posição conta, e o resultado é sempre o mesmo para a mesma entrada. */
class FusaoPosicoesTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID D = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    @Test
    void somaUmSobreKMaisPosicao() {
        // palavra: A, B, C   vetor: C, A, D
        var fundidos = FusaoPosicoes.fundir(List.of(List.of(A, B, C), List.of(C, A, D)), 60, 10);

        // A = 1/61 + 1/62; C = 1/63 + 1/61; B = 1/62; D = 1/63
        assertThat(fundidos).extracting(FusaoPosicoes.Pontuado::id).containsExactly(A, C, B, D);
        assertThat(fundidos.get(0).pontuacao()).isCloseTo(1.0 / 61 + 1.0 / 62, within(1e-12));
        assertThat(fundidos.get(1).pontuacao()).isCloseTo(1.0 / 63 + 1.0 / 61, within(1e-12));
        assertThat(fundidos.get(2).pontuacao()).isCloseTo(1.0 / 62, within(1e-12));
    }

    @Test
    void empateDesempataPelaMelhorPosicaoEDepoisPeloId() {
        // B e A só aparecem uma vez, os dois na posição 1 de listas diferentes: mesma soma e mesma melhor posição
        var fundidos = FusaoPosicoes.fundir(List.of(List.of(B), List.of(A)), 60, 10);
        assertThat(fundidos).extracting(FusaoPosicoes.Pontuado::id).containsExactly(A, B);

        // Listas simétricas: C e D têm a mesma soma (1/61 + 1/62) e a mesma melhor posição; vale o id
        var simetricos = FusaoPosicoes.fundir(List.of(List.of(D, C), List.of(C, D)), 60, 10);
        assertThat(simetricos).extracting(FusaoPosicoes.Pontuado::id).containsExactly(C, D);
    }

    @Test
    void mesmaEntradaMesmaSaidaELimiteRespeitado() {
        List<List<UUID>> entrada = List.of(List.of(A, B, C, D), List.of(D, C, B, A));
        var primeira = FusaoPosicoes.fundir(entrada, 60, 2);
        for (int i = 0; i < 20; i++) {
            assertThat(FusaoPosicoes.fundir(entrada, 60, 2)).isEqualTo(primeira);
        }
        assertThat(primeira).hasSize(2);
    }

    @Test
    void listaVaziaNaoAtrapalha() {
        var fundidos = FusaoPosicoes.fundir(List.of(List.of(B, A), List.of()), 60, 10);
        assertThat(fundidos).extracting(FusaoPosicoes.Pontuado::id).containsExactly(B, A);
    }
}
