package br.com.condominioauditoria.rag.dominio.po;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.rag.dominio.fluxo.Verificacao;
import br.com.condominioauditoria.rag.dominio.po.PrevisaoOrcamentaria.LinhaPo;
import br.com.condominioauditoria.rag.dominio.po.PrevisaoOrcamentaria.TipoLinha;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Conferência da PO com uma PO pequena montada à mão (roda sem os dados do piloto). */
class ConferenciaPoTest {

    @Test
    void poQueFechaPassaEmTudo() {
        var po = po(
                linha(TipoLinha.TOTAL, "1", "Soma das seções 1.1 a 1.9", "TOTAL DAS DESPESAS", "1050.00", null),
                linha(TipoLinha.GRUPO, "1.1", "Subtotal (soma linhas 3 a 4)", "PESSOAL", "1000.00", null),
                linha(TipoLinha.LINHA, "1.1.1", null, "Salários", "600.00", null),
                linha(TipoLinha.LINHA, "1.1.2", null, "Férias", "400.00", null),
                linha(TipoLinha.GRUPO, "1.9", "Fundos", "Fundos do Condomínio", "50.00", null),
                linha(TipoLinha.LINHA, "1.9.1", "Fundo de Reserva", "Fundo de Reserva", "30.00", "3,00%"),
                linha(TipoLinha.LINHA, "1.9.2", "Obras", "Fundo de Obras", "20.00", "2,00%"));

        List<Verificacao> resultado = ConferenciaPo.conferir(po);

        assertThat(resultado).allSatisfy(v -> assertThat(v.ok()).as(v.codigo() + ": " + v.detalhe()).isTrue());
        assertThat(resultado).extracting(Verificacao::codigo).containsExactly("SUBTOTAL_GRUPO", "SUBTOTAL_GRUPO",
                "TOTAL", "PREVISTO_MES", "FUNDO_TAXA", "CODIGO_REPETIDO");
        assertThat(ConferenciaPo.previstoDoMes(po)).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("1000.00"));
        assertThat(resultado.get(3).detalhe()).isEqualTo("1.050,00 - 50,00 = 1.000,00");
        assertThat(resultado.get(5).detalhe()).isEqualTo("nenhum código repetido");
    }

    @Test
    void subtotalDiferenteDaSomaMostraAsDuasSomas() {
        var po = po(
                linha(TipoLinha.TOTAL, "1", null, "TOTAL", "1000.00", null),
                linha(TipoLinha.GRUPO, "1.1", "Subtotal", "PESSOAL", "999.00", null),
                linha(TipoLinha.LINHA, "1.1.1", null, "Salários", "1000.00", null));

        List<Verificacao> resultado = ConferenciaPo.conferir(po);

        assertThat(resultado.getFirst().ok()).isFalse();
        assertThat(resultado.getFirst().detalhe())
                .isEqualTo("1.1 PESSOAL: soma das linhas 1.000,00; impresso 999,00; diferença -1,00");
        Verificacao total = resultado.stream().filter(v -> v.codigo().equals("TOTAL")).findFirst().orElseThrow();
        assertThat(total.ok()).isFalse();
        assertThat(total.detalhe()).isEqualTo("soma dos grupos 999,00; impresso 1.000,00; diferença 1,00");
        Verificacao previsto = resultado.stream().filter(v -> v.codigo().equals("PREVISTO_MES")).findFirst().orElseThrow();
        assertThat(previsto.ok()).isFalse();
        assertThat(previsto.detalhe()).isEqualTo("grupo de fundos não encontrado");
    }

    /** O mesmo código em duas linhas: as duas ficam e somam no grupo, e o código é apontado. */
    @Test
    void codigoRepetidoEhApontadoEAsDuasLinhasSomam() {
        var po = po(
                linha(TipoLinha.GRUPO, "1.3", "Subtotal", "CONTRATOS", "4518.93", null),
                linha(TipoLinha.LINHA, "1.3.2", "1598 - Bombas", "Servirio", "3000.00", null),
                linha(TipoLinha.LINHA, "1.3.24", "4069 - ASSESSORIA", "Vitor", "0.00", null),
                linha(TipoLinha.LINHA, "1.3.2", "1624 - Caixa D'água", "Caixa D'água", "1518.93", null));

        List<Verificacao> resultado = ConferenciaPo.conferir(po);

        assertThat(resultado.getFirst().ok()).isTrue();
        Verificacao repetido = resultado.getLast();
        assertThat(repetido.codigo()).isEqualTo("CODIGO_REPETIDO");
        assertThat(repetido.ok()).isFalse();
        assertThat(repetido.detalhe()).isEqualTo("1.3.2 aparece 2 vezes (ordens 2 e 4)");
    }

    @Test
    void fundoForaDaTaxaFalha() {
        var po = po(
                linha(TipoLinha.TOTAL, "1", null, "TOTAL", "1060.00", null),
                linha(TipoLinha.GRUPO, "1.1", "Subtotal", "PESSOAL", "1000.00", null),
                linha(TipoLinha.LINHA, "1.1.1", null, "Salários", "1000.00", null),
                linha(TipoLinha.GRUPO, "1.9", "Fundos", "Fundos", "60.00", null),
                linha(TipoLinha.LINHA, "1.9.1", "Fundo de Reserva", "Fundo de Reserva", "60.00", "5,00%"));

        Verificacao taxa = ConferenciaPo.conferir(po).stream().filter(v -> v.codigo().equals("FUNDO_TAXA")).findFirst()
                .orElseThrow();

        assertThat(taxa.ok()).isFalse();
        assertThat(taxa.detalhe()).isEqualTo("1.9.1 Fundo de Reserva: 5,00% de 1.000,00 = 50,00; impresso 60,00");
    }

    @Test
    void linhaAntesDoPrimeiroGrupoEhApontada() {
        var po = po(linha(TipoLinha.LINHA, "1.1.1", null, "Solta", "10.00", null));

        assertThat(ConferenciaPo.conferir(po)).filteredOn(v -> v.codigo().equals("LINHA_SEM_GRUPO"))
                .singleElement().satisfies(v -> {
                    assertThat(v.ok()).isFalse();
                    assertThat(v.detalhe()).isEqualTo("linhas antes do primeiro grupo: 1.1.1");
                });
    }

    @Test
    void taxaDaColunaPercentual() {
        assertThat(ConferenciaPo.taxa("3,00%")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("3.00"));
        assertThat(ConferenciaPo.taxa("-100,00%")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("-100"));
        assertThat(ConferenciaPo.taxa("média")).isEmpty();
        assertThat(ConferenciaPo.taxa(null)).isEmpty();
    }

    private static PrevisaoOrcamentaria po(LinhaPo... linhas) {
        List<LinhaPo> numeradas = new ArrayList<>();
        for (int i = 0; i < linhas.length; i++) {
            LinhaPo l = linhas[i];
            numeradas.add(new LinhaPo(i + 1, 1, l.tipo(), l.codigoImpresso(), l.conta(), l.contaTexto(), l.marca(),
                    l.descricao(), l.orcadoAnterior(), l.orcado(), l.percentualTexto(), l.observacoes()));
        }
        return new PrevisaoOrcamentaria("PROPOSTA ORÇAMENTÁRIA 2026 / 2027", "2026 / 2027",
                List.of("2025/2026", "2026/2027"), numeradas);
    }

    private static LinhaPo linha(TipoLinha tipo, String codigo, String contaTexto, String descricao, String orcado,
            String percentual) {
        return new LinhaPo(0, 1, tipo, codigo, null, contaTexto, null, descricao, new BigDecimal("0.00"),
                new BigDecimal(orcado), percentual, null);
    }
}
