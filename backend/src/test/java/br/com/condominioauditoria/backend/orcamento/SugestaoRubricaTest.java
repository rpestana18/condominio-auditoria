package br.com.condominioauditoria.backend.orcamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.LinhaPoLida;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.TipoLinhaPo;
import br.com.condominioauditoria.backend.orcamento.SugestaoRubrica.Confirmada;
import br.com.condominioauditoria.backend.orcamento.SugestaoRubrica.LinhaComGrupo;
import br.com.condominioauditoria.backend.orcamento.SugestaoRubrica.SemSugestao;
import br.com.condominioauditoria.backend.orcamento.SugestaoRubrica.Sugerida;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-11.7: a sugestão só existe quando uma rubrica só casa pela conta da PO e pelo grupo. Função pura. */
class SugestaoRubricaTest {

    private final PrevisaoOrcamentaria po = new PrevisaoOrcamentaria(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64));
    private final PrevisaoOrcamentaria outra = new PrevisaoOrcamentaria(UUID.randomUUID(), UUID.randomUUID(),
            "b".repeat(64));

    @Test
    void contaRepetidaNoMesmoGrupoDestaPoFicaSemSugestao() {
        LinhaComGrupo l1 = linha(po, "1.3.5", "1606 - Aparelhos de Ginástica", "1.3");
        LinhaComGrupo l2 = linha(po, "1.3.9", "1606 - Aparelhos de Ginástica", "1.3");
        UUID rubrica = UUID.randomUUID();
        List<Confirmada> confirmadas = List.of(new Confirmada(linha(outra, "1.3.5", "1606 - Aparelhos de Ginástica",
                "1.3"), rubrica));

        var r = SugestaoRubrica.sugerir(List.of(l1, l2), List.of(l1, l2), List.of(), confirmadas);

        assertThat(r.values()).allSatisfy(x -> {
            assertThat(x).isInstanceOf(SemSugestao.class);
            assertThat(x.motivo()).contains("aparece em mais de uma linha do grupo 1.3");
        });
    }

    @Test
    void contaEmDuasRubricasNoMesmoGrupoFicaSemSugestao() {
        LinhaComGrupo nova = linha(po, "1.3.5", "1606 - Aparelhos de Ginástica", "1.3");
        List<Confirmada> confirmadas = List.of(
                new Confirmada(linha(outra, "1.3.5", "1606 - Aparelhos de Ginástica", "1.3"), UUID.randomUUID()),
                new Confirmada(linha(outra, "1.3.6", "1606 - Aparelhos de Ginástica", "1.3"), UUID.randomUUID()));

        var r = SugestaoRubrica.sugerir(List.of(nova), List.of(nova), List.of(), confirmadas).get(nova.linha().getId());

        assertThat(r).isInstanceOf(SemSugestao.class);
        assertThat(r.motivo()).contains("está em 2 rubricas");
    }

    @Test
    void mesmaContaEmOutroGrupoNaoCasa() {
        LinhaComGrupo nova = linha(po, "1.7.2", "1606 - Aparelhos de Ginástica", "1.7");
        List<Confirmada> confirmadas = List.of(
                new Confirmada(linha(outra, "1.3.5", "1606 - Aparelhos de Ginástica", "1.3"), UUID.randomUUID()));

        var r = SugestaoRubrica.sugerir(List.of(nova), List.of(nova), List.of(), confirmadas).get(nova.linha().getId());

        assertThat(r).isInstanceOf(SemSugestao.class);
        assertThat(r.motivo()).startsWith("nenhuma linha confirmada com a conta da PO");
    }

    @Test
    void comparaSemAcentoNemCaixaENuncaPeloCodigoDoItem() {
        UUID rubrica = UUID.randomUUID();
        LinhaComGrupo nova = linha(po, "1.3.7", "1682 - SINDICATURA  PROFISSIONAL", "1.3");
        List<Confirmada> confirmadas = List.of(
                new Confirmada(linha(outra, "1.3.20", "1682 - Sindicatura Profissional", "1.3"), rubrica));

        var r = SugestaoRubrica.sugerir(List.of(nova), List.of(nova), List.of(), confirmadas).get(nova.linha().getId());

        assertThat(r).isEqualTo(new Sugerida(rubrica, OrigemRubrica.CONTA_PO,
                "mesma conta da PO e mesmo grupo: 1682 - SINDICATURA  PROFISSIONAL, 1.3"));
    }

    @Test
    void versaoAnteriorVemAntesDaContaDaPo() {
        UUID daAnterior = UUID.randomUUID();
        LinhaComGrupo nova = linha(po, "1.3.20", "1682 - Sindicatura Profissional", "1.3");
        Confirmada anterior = new Confirmada(linha(outra, "1.3.20", "1682 - Sindicatura Profissional", "1.3"),
                daAnterior);
        Confirmada deOutroExercicio = new Confirmada(linha(outra, "1.3.21", "1682 - Sindicatura Profissional", "1.3"),
                UUID.randomUUID());

        var r = SugestaoRubrica.sugerir(List.of(nova), List.of(nova), List.of(anterior),
                List.of(anterior, deOutroExercicio)).get(nova.linha().getId());

        assertThat(r).isInstanceOfSatisfying(Sugerida.class, s -> {
            assertThat(s.rubricaId()).isEqualTo(daAnterior);
            assertThat(s.origem()).isEqualTo(OrigemRubrica.VERSAO_ANTERIOR);
        });
    }

    private static LinhaComGrupo linha(PrevisaoOrcamentaria previsao, String codigo, String conta, String grupo) {
        LinhaPo l = GravacaoPrevisao.linha(previsao, new LinhaPoLida(1, 1, TipoLinhaPo.LINHA, codigo, conta, null, null,
                "Fornecedor " + codigo, BigDecimal.ZERO.setScale(2), new BigDecimal("100.00"), null, null));
        return new LinhaComGrupo(l, grupo);
    }
}
