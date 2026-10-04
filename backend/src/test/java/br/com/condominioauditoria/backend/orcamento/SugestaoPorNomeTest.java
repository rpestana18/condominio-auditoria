package br.com.condominioauditoria.backend.orcamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-03.1.4 e RF-03.1.5: sugestão pelo nome, sem IA e sem nunca usar o número da conta. */
class SugestaoPorNomeTest {

    private final SugestaoPorNome sugestao = new SugestaoPorNome(PropriedadesDepara.padrao());
    private final List<LinhaPo> linhas = PoDoPiloto.padrao().linhasGravadas(
            new PrevisaoOrcamentaria(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64)));
    private final List<LinhaPo> candidatas = SugestaoPorNome.candidatas(EstruturaPo.de(linhas));

    @Test
    void conta1621MaterialHidraulicoVaiPara178ENuncaPara1323() {
        var r = sugestao.sugerir("MATERIAL HIDRÁULICO", candidatas);

        assertThat(r).isInstanceOf(SugestaoPorNome.Sugerida.class);
        var s = (SugestaoPorNome.Sugerida) r;
        assertThat(s.linha().getCodigoEfetivo()).isEqualTo("1.7.8");
        assertThat(s.motivo()).contains("fluxo: MATERIAL HIDRAULICO").contains("PO 1.7.8").doesNotContain("1621");
    }

    @Test
    void numeroDaContaNuncaCasa() {
        // Mesmo com o número 1621 no nome do fluxo, a linha 1.3.23 ("1621 - Interfones") não ganha nada
        assertThat(sugestao.sugerir("1621", candidatas)).isInstanceOf(SugestaoPorNome.SemSugestao.class);
        var r = sugestao.sugerir("1621 MATERIAL HIDRAULICO", candidatas);
        assertThat(((SugestaoPorNome.Sugerida) r).linha().getCodigoEfetivo()).isEqualTo("1.7.8");
        assertThat(sugestao.normalizar("1621 - Interfones")).containsExactly("INTERFONES");
        for (LinhaPo l : candidatas) {
            assertThat(String.join(" ", sugestao.normalizar(l.getConta()))).doesNotContainPattern("[0-9]");
        }
    }

    @Test
    void normalizaAcentoPontuacaoAbreviacaoEPalavraVazia() {
        assertThat(sugestao.normalizar("Desp. c/ Mat. de Construção 13°")).containsExactly("DESPESA", "MATERIAL",
                "CONSTRUCAO");
        assertThat(sugestao.normalizar(null)).isEmpty();
    }

    @Test
    void empateNaoSugere() {
        // "Caixa D'água" está na conta da segunda 1.3.2 e não em outra linha: sugere; dois iguais: empate
        var dupla = new java.util.ArrayList<>(candidatas);
        dupla.add(linhaExtra("1.8.8", "1624 - Caixa D'água", "Limpeza caixa d'água"));
        var r = sugestao.sugerir("CAIXA D'ÁGUA", dupla);

        assertThat(r).isInstanceOf(SugestaoPorNome.SemSugestao.class);
        assertThat(r.motivo()).contains("empate").contains("1.3.2").contains("1.8.8");
    }

    @Test
    void notaAbaixoDoMinimoNaoSugere() {
        var r = sugestao.sugerir("OUTRAS DESPESAS C/PESSOAL DIVERSO", candidatas);

        assertThat(r).isInstanceOf(SugestaoPorNome.SemSugestao.class);
    }

    @Test
    void rateioAParteEFundosNaoSaoCandidatos() {
        assertThat(candidatas).noneMatch(l -> l.getMarca() == MarcaPo.RATEIO_A_PARTE);
        assertThat(candidatas).noneMatch(l -> l.getCodigoEfetivo().startsWith("1.9"));
        assertThat(sugestao.sugerir("FUNDO DE RESERVA", candidatas)).isInstanceOf(SugestaoPorNome.SemSugestao.class);
    }

    @Test
    void mesmoInsumoMesmaSugestao() {
        var a = sugestao.sugerir("MATERIAL HIDRÁULICO", candidatas);
        var b = new SugestaoPorNome(PropriedadesDepara.padrao()).sugerir("MATERIAL HIDRÁULICO", candidatas);
        assertThat(a.motivo()).isEqualTo(b.motivo());
    }

    private static LinhaPo linhaExtra(String codigo, String conta, String descricao) {
        var po = new PrevisaoOrcamentaria(UUID.randomUUID(), UUID.randomUUID(), "b".repeat(64));
        return new LinhaPo(po, 999, 1, TipoLinhaPo.LINHA, codigo, conta, null, null, descricao,
                java.math.BigDecimal.ZERO.setScale(2), java.math.BigDecimal.ONE.setScale(2), null, null);
    }
}
