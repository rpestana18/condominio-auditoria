package br.com.condominioauditoria.rag.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Rank fusion (RRF, k = 60): only the rank counts, and the result is always the same for the same input. */
class RankFusionTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID D = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    @Test
    void sumsOneOverKPlusRank() {
        // keyword: A, B, C   vector: C, A, D
        var fused = RankFusion.fuse(List.of(List.of(A, B, C), List.of(C, A, D)), 60, 10);

        // A = 1/61 + 1/62; C = 1/63 + 1/61; B = 1/62; D = 1/63
        assertThat(fused).extracting(RankFusion.Scored::id).containsExactly(A, C, B, D);
        assertThat(fused.get(0).score()).isCloseTo(1.0 / 61 + 1.0 / 62, within(1e-12));
        assertThat(fused.get(1).score()).isCloseTo(1.0 / 63 + 1.0 / 61, within(1e-12));
        assertThat(fused.get(2).score()).isCloseTo(1.0 / 62, within(1e-12));
    }

    @Test
    void tieBreaksByBestRankThenById() {
        // B and A appear only once, both at rank 1 of different lists: same sum and same best rank
        var fused = RankFusion.fuse(List.of(List.of(B), List.of(A)), 60, 10);
        assertThat(fused).extracting(RankFusion.Scored::id).containsExactly(A, B);

        // Symmetric lists: C and D have the same sum (1/61 + 1/62) and the same best rank; the id decides
        var symmetric = RankFusion.fuse(List.of(List.of(D, C), List.of(C, D)), 60, 10);
        assertThat(symmetric).extracting(RankFusion.Scored::id).containsExactly(C, D);
    }

    @Test
    void sameInputSameOutputAndLimitRespected() {
        List<List<UUID>> input = List.of(List.of(A, B, C, D), List.of(D, C, B, A));
        var first = RankFusion.fuse(input, 60, 2);
        for (int i = 0; i < 20; i++) {
            assertThat(RankFusion.fuse(input, 60, 2)).isEqualTo(first);
        }
        assertThat(first).hasSize(2);
    }

    @Test
    void emptyListDoesNotInterfere() {
        var fused = RankFusion.fuse(List.of(List.of(B, A), List.of()), 60, 10);
        assertThat(fused).extracting(RankFusion.Scored::id).containsExactly(B, A);
    }
}
