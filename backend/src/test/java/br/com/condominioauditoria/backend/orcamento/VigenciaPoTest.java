package br.com.condominioauditoria.backend.orcamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-03.1.3: só uma PO vale para cada mês de cada condomínio. */
class VigenciaPoTest {

    @Test
    void poDoPilotoValeDeMaio2026AAbril2027() {
        PrevisaoOrcamentaria po = confirmada(YearMonth.of(2026, 5), YearMonth.of(2027, 4));

        assertThat(VigenciaPo.vigenteNoMes(List.of(po), YearMonth.of(2026, 9))).contains(po);
        assertThat(VigenciaPo.vigenteNoMes(List.of(po), YearMonth.of(2026, 5))).contains(po);
        assertThat(VigenciaPo.vigenteNoMes(List.of(po), YearMonth.of(2027, 4))).contains(po);
        // "sem PO aprovada para este mês"
        assertThat(VigenciaPo.vigenteNoMes(List.of(po), YearMonth.of(2026, 4))).isEmpty();
        assertThat(VigenciaPo.vigenteNoMes(List.of(po), YearMonth.of(2027, 5))).isEmpty();
    }

    @Test
    void poSoLidaNaoValeParaNenhumMes() {
        PrevisaoOrcamentaria lida = lida();

        assertThat(VigenciaPo.de(lida)).isEmpty();
        assertThat(VigenciaPo.vigenteNoMes(List.of(lida), YearMonth.of(2026, 9))).isEmpty();
    }

    @Test
    void substituidaValeAteOMesAnteriorAoDaNovaVersao() {
        PrevisaoOrcamentaria v1 = confirmada(YearMonth.of(2026, 5), YearMonth.of(2027, 4));
        v1.substituirAPartirDe(YearMonth.of(2026, 9));

        VigenciaPo vigencia = VigenciaPo.de(v1).orElseThrow();
        assertThat(vigencia.fim()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(vigencia.sobrepoe(YearMonth.of(2026, 9), YearMonth.of(2027, 4))).isFalse();
        assertThat(vigencia.sobrepoe(YearMonth.of(2026, 8), YearMonth.of(2027, 4))).isTrue();
    }

    @Test
    void substituidaDesdeOInicioNaoValeMais() {
        PrevisaoOrcamentaria v1 = confirmada(YearMonth.of(2026, 5), YearMonth.of(2027, 4));
        v1.substituirAPartirDe(YearMonth.of(2026, 5));

        assertThat(VigenciaPo.de(v1)).isEmpty();
    }

    private static PrevisaoOrcamentaria lida() {
        PrevisaoOrcamentaria p = new PrevisaoOrcamentaria(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64));
        p.registrarLeitura("po-protest", "t", "e", "a", "b", EstadoPrevisao.LIDA, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ONE, new BigDecimal("0.01"), Instant.now());
        return p;
    }

    private static PrevisaoOrcamentaria confirmada(YearMonth inicio, YearMonth fim) {
        PrevisaoOrcamentaria p = lida();
        p.confirmar(1, inicio, fim, null, true, null, false, null, "admin", Instant.now());
        return p;
    }

    @Test
    void foraDoPrimeiroTrimestrePelaDataDaAtaOuPeloInicioDoExercicio() {
        PrevisaoOrcamentaria maio = lida();
        maio.confirmar(1, YearMonth.of(2026, 5), YearMonth.of(2027, 4), UUID.randomUUID(), false,
                LocalDate.of(2026, 5, 20), false, null, "admin", Instant.now());
        PrevisaoOrcamentaria marcoSemAta = confirmada(YearMonth.of(2026, 3), YearMonth.of(2027, 2));

        assertThat(ConsultaPrevisao.foraDoPrimeiroTrimestre(maio)).isTrue();
        assertThat(ConsultaPrevisao.foraDoPrimeiroTrimestre(marcoSemAta)).isFalse();
    }
}
