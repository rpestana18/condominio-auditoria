package br.com.condominioauditoria.api.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.auditoria.RegistroAchados.Apurado;
import br.com.condominioauditoria.api.auditoria.RegistroAchados.Evidencia;
import br.com.condominioauditoria.api.auditoria.RegistroAchados.Sincronizacao;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-03.1.12 e Q27: recálculo idempotente, "não se aplica mais", reabertura do mesmo achado, marcação humana mantida. */
class RegistroAchadosSincronizacaoTest {

    private static final YearMonth SETEMBRO = YearMonth.of(2026, 9);
    private static final Set<String> REGRAS = Set.of(RegraContaSemLinhaPo.CODIGO);

    private final UUID condominio = UUID.randomUUID();
    private final List<Achado> achados = new ArrayList<>();
    private final List<AchadoEvidencia> evidencias = new ArrayList<>();
    private final List<EventoAchado> eventos = new ArrayList<>();
    private final RegistroAchados registro;

    RegistroAchadosSincronizacaoTest() {
        AchadoRepository achadoRepo = mock(AchadoRepository.class);
        when(achadoRepo.save(any())).thenAnswer(i -> {
            if (!achados.contains(i.<Achado>getArgument(0))) {
                achados.add(i.getArgument(0));
            }
            return i.getArgument(0);
        });
        when(achadoRepo.findByCondominioIdAndCompetenciaAndRegraIn(any(), any(), any())).thenAnswer(i -> achados
                .stream().filter(a -> a.getCondominioId().equals(i.getArgument(0))
                        && a.getCompetencia().atDay(1).equals(i.getArgument(1))
                        && i.<Collection<String>>getArgument(2).contains(a.getRegra())).toList());
        AchadoEvidenciaRepository evidenciaRepo = mock(AchadoEvidenciaRepository.class);
        when(evidenciaRepo.save(any())).thenAnswer(i -> {
            evidencias.add(i.getArgument(0));
            return i.getArgument(0);
        });
        EventoAchadoRepository eventoRepo = mock(EventoAchadoRepository.class);
        when(eventoRepo.save(any())).thenAnswer(i -> {
            eventos.add(i.getArgument(0));
            return i.getArgument(0);
        });
        registro = new RegistroAchados(achadoRepo, evidenciaRepo, eventoRepo);
    }

    @Test
    void doisRecalculosComOMesmoInsumoDeixamUmAchadoSo() {
        Sincronizacao primeiro = registro.sincronizar(condominio, SETEMBRO, REGRAS, List.of(conta8888()),
                gatilho("fluxo gravado"));
        Sincronizacao segundo = registro.sincronizar(condominio, SETEMBRO, REGRAS, List.of(conta8888()),
                gatilho("fluxo gravado de novo"));

        assertThat(primeiro).isEqualTo(new Sincronizacao(1, 0, 0, 0));
        assertThat(segundo).isEqualTo(new Sincronizacao(0, 0, 0, 1));
        assertThat(achados).singleElement().satisfies(a -> {
            assertThat(a.getEstado()).isEqualTo(EstadoAchado.ABERTO);
            assertThat(a.getSeveridade()).isEqualTo(Severidade.ATENCAO);
        });
        assertThat(evidencias).hasSize(1);
        assertThat(eventos).hasSize(1);
    }

    @Test
    void condicaoQueDeixaDeExistirViraNaoSeAplicaMaisEVoltaNoMesmoAchado() {
        registro.sincronizar(condominio, SETEMBRO, REGRAS, List.of(conta8888()), gatilho("fluxo gravado"));
        Achado a = achados.getFirst();

        registro.sincronizar(condominio, SETEMBRO, REGRAS, List.of(), gatilho("de-para da conta 8888 confirmado"));
        registro.sincronizar(condominio, SETEMBRO, REGRAS, List.of(), gatilho("outra mudança"));

        assertThat(a.getEstado()).isEqualTo(EstadoAchado.NAO_SE_APLICA_MAIS);
        assertThat(a.getEstadoMotivo()).isEqualTo("de-para da conta 8888 confirmado por admin em 04/10/2026");
        assertThat(eventos).hasSize(2);

        registro.sincronizar(condominio, SETEMBRO, REGRAS, List.of(conta8888()), gatilho("de-para desfeito"));

        assertThat(achados).singleElement().isSameAs(a);
        assertThat(a.getEstado()).isEqualTo(EstadoAchado.ABERTO);
        assertThat(eventos).extracting(EventoAchado::getEstadoNovo).containsExactly(EstadoAchado.ABERTO,
                EstadoAchado.NAO_SE_APLICA_MAIS, EstadoAchado.ABERTO);
        assertThat(evidencias).hasSize(1);
    }

    @Test
    void achadoMarcadoPorPessoaMantemOEstadoESoGanhaHistorico() throws Exception {
        registro.sincronizar(condominio, SETEMBRO, REGRAS, List.of(conta8888()), gatilho("fluxo gravado"));
        Achado a = achados.getFirst();
        var estado = Achado.class.getDeclaredField("estado");
        estado.setAccessible(true);
        estado.set(a, EstadoAchado.JUSTIFICADO); // marcação humana (RF-02.8, tela de achados ainda não existe)

        registro.sincronizar(condominio, SETEMBRO, REGRAS, List.of(), gatilho("de-para da conta 8888 confirmado"));

        assertThat(a.getEstado()).isEqualTo(EstadoAchado.JUSTIFICADO);
        assertThat(a.isCondicaoPresente()).isFalse();
        assertThat(eventos.getLast().getEstadoNovo()).isEqualTo(EstadoAchado.JUSTIFICADO);
        assertThat(eventos.getLast().getMotivo()).contains("estado marcado por pessoa mantido");
    }

    @Test
    void regraNaoAvaliadaNoMesNaoMudaOsAchadosDela() {
        registro.sincronizar(condominio, SETEMBRO, Set.of(RegraContaSemLinhaPo.CODIGO, RegraExcessoMes.CODIGO),
                List.of(conta8888(), new Apurado(RegraExcessoMes.CODIGO, "1", Severidade.CRITICO, "fundo-condominio",
                        "excesso de 21,9% do previsto do mês", List.of())), gatilho("fluxo gravado"));

        registro.sincronizar(condominio, SETEMBRO, REGRAS, List.of(conta8888()), gatilho("limite descadastrado"));

        assertThat(achados).hasSize(2).allMatch(a -> a.getEstado() == EstadoAchado.ABERTO);
    }

    private static Apurado conta8888() {
        return new Apurado(RegraContaSemLinhaPo.CODIGO, RegraContaSemLinhaPo.VERSAO, RegraContaSemLinhaPo.SEVERIDADE,
                RegraContaSemLinhaPo.alvo("8888"), "Conta 8888 sem linha da PO",
                List.of(new Evidencia(UUID.randomUUID(), "a".repeat(64), 3, "Lançamento de 30/09/2026", null)));
    }

    private static GatilhoRecalculo gatilho(String descricao) {
        return new GatilhoRecalculo(descricao, "admin", Instant.parse("2026-10-04T15:00:00Z"));
    }
}
