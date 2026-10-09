package br.com.condominioauditoria.api.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Conv. 20.1 (RF-03.1.3): fundo de reserva acima do teto gera achado "atenção"; comparação exata. */
class RegraTetoFundoReservaTest {

    private static final BigDecimal CINCO = new BigDecimal("5.0000");

    @Test
    void pilotoComTresPorCentoNaoPassaDoTeto() {
        var a = RegraTetoFundoReserva.avaliar(new BigDecimal("13548.60"), new BigDecimal("451620.12"), CINCO)
                .orElseThrow();

        assertThat(a.acimaDoTeto()).isFalse();
        assertThat(a.percentualExibido()).isEqualTo("3,0%");
        assertThat(a.tetoExibido()).isEqualTo("5%");
    }

    @Test
    void exatamenteCincoPorCentoNaoPassaEUmCentavoAMaisPassa() {
        BigDecimal previsto = new BigDecimal("451620.00");

        assertThat(RegraTetoFundoReserva.avaliar(new BigDecimal("22581.00"), previsto, CINCO).orElseThrow()
                .acimaDoTeto()).isFalse();
        assertThat(RegraTetoFundoReserva.avaliar(new BigDecimal("22581.01"), previsto, CINCO).orElseThrow()
                .acimaDoTeto()).isTrue();
    }

    @Test
    void semPrevistoNaoHaBaseParaOPercentual() {
        assertThat(RegraTetoFundoReserva.avaliar(new BigDecimal("100.00"), BigDecimal.ZERO, CINCO)).isEmpty();
    }

    @Test
    void registrarDeNovoNaoDuplicaOAchado() {
        AchadoRepository achados = mock(AchadoRepository.class);
        AchadoEvidenciaRepository evidencias = mock(AchadoEvidenciaRepository.class);
        List<Achado> gravados = new ArrayList<>();
        when(achados.save(any())).thenAnswer(i -> {
            gravados.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(achados.findByCondominioIdAndRegraAndCompetenciaAndAlvo(any(), any(), any(), any()))
                .thenAnswer(i -> gravados.stream().findFirst());
        RegistroAchados registro = new RegistroAchados(achados, evidencias, mock(EventoAchadoRepository.class));
        UUID condominio = UUID.randomUUID();
        var prova = List.of(new RegistroAchados.Evidencia(UUID.randomUUID(), "a".repeat(64), 1, "PO, linha 1.9.1", null));

        Achado primeiro = registro.registrar(condominio, RegraTetoFundoReserva.CODIGO, "1", Severidade.ATENCAO,
                YearMonth.of(2026, 5), "previsao:x:linha:y", "texto", prova);
        Achado segundo = registro.registrar(condominio, RegraTetoFundoReserva.CODIGO, "1", Severidade.ATENCAO,
                YearMonth.of(2026, 5), "previsao:x:linha:y", "texto", prova);

        assertThat(segundo).isSameAs(primeiro);
        assertThat(primeiro.getEstado()).isEqualTo(EstadoAchado.ABERTO);
        verify(achados, times(1)).save(any());
        verify(evidencias, times(1)).save(any());
        assertThat(Optional.of(primeiro.getCompetencia())).contains(YearMonth.of(2026, 5));
    }
}
