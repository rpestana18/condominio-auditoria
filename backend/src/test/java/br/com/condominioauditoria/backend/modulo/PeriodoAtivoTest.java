package br.com.condominioauditoria.backend.modulo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Períodos ativos calculados da trilha (RF-10.6). */
class PeriodoAtivoTest {

    private static final UUID CONDOMINIO = UUID.randomUUID();
    private static final Instant LIGOU = Instant.parse("2026-11-01T13:00:00Z");
    private static final Instant DESLIGOU = Instant.parse("2026-12-15T18:30:00Z");

    @Test
    void ligadoEmNovembroEDesligadoEmDezembroDaUmPeriodoComQuemLigouEQuemDesligou() {
        var periodos = PeriodoAtivo.calcular(Modulos.ASSISTENTE, false, List.of(
                evento(false, true, "ana", LIGOU, "Contrato assinado"),
                evento(true, false, "bruno", DESLIGOU, "Fim do teste")));

        assertThat(periodos).containsExactly(new PeriodoAtivo(Modulos.ASSISTENTE, LIGOU, DESLIGOU, "ana",
                "Contrato assinado", "bruno", "Fim do teste"));
    }

    @Test
    void religadoAbreUmSegundoPeriodoAindaAberto() {
        Instant religou = Instant.parse("2027-02-01T10:00:00Z");
        var periodos = PeriodoAtivo.calcular(Modulos.ASSISTENTE, false, List.of(
                evento(false, true, "ana", LIGOU, "a"),
                evento(true, false, "ana", DESLIGOU, "b"),
                evento(false, true, "carla", religou, "c")));

        assertThat(periodos).hasSize(2);
        assertThat(periodos.get(1).inicio()).isEqualTo(religou);
        assertThat(periodos.get(1).fim()).isNull();
        assertThat(periodos.get(1).ligadoPor()).isEqualTo("carla");
        assertThat(periodos.get(1).desligadoPor()).isNull();
    }

    @Test
    void semEventosEDesligadoPorPadraoNaoHaPeriodo() {
        assertThat(PeriodoAtivo.calcular(Modulos.ASSISTENTE, false, List.of())).isEmpty();
    }

    @Test
    void moduloLigadoPorPadraoComecaAbertoSemInicio() {
        var periodos = PeriodoAtivo.calcular("OUTRO", true, List.of(evento(true, false, "ana", DESLIGOU, "x")));

        assertThat(periodos).containsExactly(new PeriodoAtivo("OUTRO", null, DESLIGOU, null, null, "ana", "x"));
    }

    @Test
    void mesmoConjuntoDeEventosDaSempreOMesmoResultado() {
        var eventos = List.of(evento(false, true, "ana", LIGOU, "a"), evento(true, false, "ana", DESLIGOU, "b"));

        assertThat(PeriodoAtivo.calcular(Modulos.ASSISTENTE, false, eventos))
                .isEqualTo(PeriodoAtivo.calcular(Modulos.ASSISTENTE, false, eventos));
    }

    @Test
    void periodoTocaOIntervaloPedido() {
        var fechado = new PeriodoAtivo(Modulos.ASSISTENTE, LIGOU, DESLIGOU, "a", "m", "b", "n");
        var aberto = new PeriodoAtivo(Modulos.ASSISTENTE, LIGOU, null, "a", "m", null, null);

        assertThat(fechado.tocaIntervalo(Instant.parse("2026-12-01T03:00:00Z"), Instant.parse("2027-01-01T03:00:00Z")))
                .isTrue();
        assertThat(fechado.tocaIntervalo(Instant.parse("2027-01-01T03:00:00Z"), Instant.parse("2027-02-01T03:00:00Z")))
                .isFalse();
        assertThat(fechado.tocaIntervalo(Instant.parse("2026-10-01T03:00:00Z"), Instant.parse("2026-11-01T03:00:00Z")))
                .isFalse();
        assertThat(aberto.tocaIntervalo(Instant.parse("2030-01-01T03:00:00Z"), Instant.parse("2030-02-01T03:00:00Z")))
                .isTrue();
    }

    private static EventoModulo evento(boolean antes, boolean depois, String usuario, Instant quando, String motivo) {
        return new EventoModulo(CONDOMINIO, Modulos.ASSISTENTE, antes, depois, usuario, quando, motivo);
    }
}
