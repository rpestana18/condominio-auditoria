package br.com.condominioauditoria.api.orcamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.api.orcamento.DeparaDtos.FiltroDepara;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Golden do de-para (RF-03.1.4, RF-03.1.5 e RF-03.1.15) com a PO e o fluxo reais de setembro/2026 e o mapa das 73
 * contas do piloto. Pulado sem data/golden/privado.
 */
class DeparaGoldenTest {

    @Test
    void planilhaDas73ContasEntraTodaSugeridaENaoLigaNenhumaConta() {
        GoldenSetembro g = golden();
        Optional<String> mapa = GoldenSetembro.mapa();
        assumeTrue(mapa.isPresent(), "mapa do piloto ausente no golden privado");

        var r = g.cenario.depara.carregarPlanilha(g.cenario.condominioId, g.po.getId(), "mapa-contas-fluxo-para-PO.csv",
                mapa.get(), "admin");

        assertThat(r.recusadas()).isEmpty();
        assertThat(r.ignoradas()).isEmpty();
        assertThat(r.aceitas()).isEqualTo(73);
        var lista = g.cenario.depara.listar(g.cenario.condominioId, g.po.getId(), FiltroDepara.TODAS);
        assertThat(lista.resumo()).isEqualTo(new DeparaDtos.ResumoDepara(73, 0, 73, 0, 0));
        assertThat(g.cenario.deparas).allMatch(d -> d.getEstado() == EstadoDepara.SUGERIDO);
        // Sugerido não liga conta nenhuma: os números só usam o confirmado (o cálculo do mês está no passo 7)
        assertThat(DeparaEfetivo.confirmados(g.cenario.deparas)).isEmpty();

        DeparaConta hidraulico = g.cenario.deparas.stream().filter(d -> d.getContaCodigo().equals("1621")).findFirst()
                .orElseThrow();
        assertThat(hidraulico.getLinhaPoId()).isEqualTo(g.linha("1.7.8").getId()).isNotEqualTo(g.linha("1.3.23").getId());
        assertThat(g.cenario.deparas.stream().filter(d -> d.getContaCodigo().equals("1073")).findFirst().orElseThrow()
                .getLinhaPoId()).isEqualTo(g.linha("1.3.25").getId());
        assertThat(g.cenario.eventosDepara).hasSize(73);
    }

    @Test
    void sugestaoPeloNomeLiga1621A178ENuncaA1323() {
        GoldenSetembro g = golden();

        var r = g.cenario.depara.sugerir(g.cenario.condominioId, g.po.getId(), "admin");

        DeparaConta hidraulico = g.cenario.deparas.stream().filter(d -> d.getContaCodigo().equals("1621")).findFirst()
                .orElseThrow();
        assertThat(hidraulico.getLinhaPoId()).isEqualTo(g.linha("1.7.8").getId());
        assertThat(hidraulico.getOrigem()).isEqualTo(OrigemDepara.NOME);
        assertThat(g.cenario.deparas).noneMatch(d -> g.linha("1.3.23").getId().equals(d.getLinhaPoId()));
        assertThat(g.cenario.deparas).allMatch(d -> d.getEstado() == EstadoDepara.SUGERIDO);
        assertThat(r.criadas() + r.semSugestao().size()).isEqualTo(73);

        // Medida da sugestão pelo nome contra o mapa do piloto: nenhuma sugestão diverge do mapa
        Optional<String> mapa = GoldenSetembro.mapa();
        assumeTrue(mapa.isPresent());
        Map<String, String> esperado = new HashMap<>();
        mapa.get().lines().skip(1).map(l -> l.split(";")).forEach(c -> esperado.put(c[0], c[1]));
        Map<java.util.UUID, String> codigo = new HashMap<>();
        g.cenario.linhas.forEach(l -> codigo.put(l.getId(), l.getCodigoEfetivo()));
        long certas = g.cenario.deparas.stream()
                .filter(d -> codigo.get(d.getLinhaPoId()).equals(esperado.get(d.getContaCodigo()))).count();
        assertThat(certas).isEqualTo(r.criadas());
        assertThat(r.criadas()).isGreaterThanOrEqualTo(40);
    }

    private static GoldenSetembro golden() {
        Optional<GoldenSetembro> g = GoldenSetembro.carregar();
        assumeTrue(g.isPresent(), "golden privado ausente");
        return g.get();
    }
}
