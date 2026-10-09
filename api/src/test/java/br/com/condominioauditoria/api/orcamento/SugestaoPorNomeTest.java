package br.com.condominioauditoria.api.orcamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import br.com.condominioauditoria.api.model.enums.BudgetLineType;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-03.1.4 e RF-03.1.5: sugestão pelo nome, sem IA e sem nunca usar o número da conta. */
class SugestaoPorNomeTest {

    private final SugestaoPorNome sugestao = new SugestaoPorNome(PropriedadesDepara.padrao());
    private final List<BudgetLine> linhas = PoDoPiloto.padrao().linhasGravadas(
            new Budget(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64)));
    private final List<BudgetLine> candidatas = SugestaoPorNome.candidatas(BudgetStructure.of(linhas));

    @Test
    void conta1621MaterialHidraulicoVaiPara178ENuncaPara1323() {
        var r = sugestao.sugerir("MATERIAL HIDRÁULICO", candidatas);

        assertThat(r).isInstanceOf(SugestaoPorNome.Sugerida.class);
        var s = (SugestaoPorNome.Sugerida) r;
        assertThat(s.linha().getEffectiveCode()).isEqualTo("1.7.8");
        assertThat(s.motivo()).contains("fluxo: MATERIAL HIDRAULICO").contains("PO 1.7.8").doesNotContain("1621");
    }

    @Test
    void numeroDaContaNuncaCasa() {
        // Mesmo com o número 1621 no nome do fluxo, a linha 1.3.23 ("1621 - Interfones") não ganha nada
        assertThat(sugestao.sugerir("1621", candidatas)).isInstanceOf(SugestaoPorNome.SemSugestao.class);
        var r = sugestao.sugerir("1621 MATERIAL HIDRAULICO", candidatas);
        assertThat(((SugestaoPorNome.Sugerida) r).linha().getEffectiveCode()).isEqualTo("1.7.8");
        assertThat(sugestao.normalizar("1621 - Interfones")).containsExactly("INTERFONES");
        for (BudgetLine l : candidatas) {
            assertThat(String.join(" ", sugestao.normalizar(l.getAccount()))).doesNotContainPattern("[0-9]");
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
        assertThat(candidatas).noneMatch(l -> l.getMark() == BudgetLineMark.RATEIO_A_PARTE);
        assertThat(candidatas).noneMatch(l -> l.getEffectiveCode().startsWith("1.9"));
        assertThat(sugestao.sugerir("FUNDO DE RESERVA", candidatas)).isInstanceOf(SugestaoPorNome.SemSugestao.class);
    }

    @Test
    void mesmoInsumoMesmaSugestao() {
        var a = sugestao.sugerir("MATERIAL HIDRÁULICO", candidatas);
        var b = new SugestaoPorNome(PropriedadesDepara.padrao()).sugerir("MATERIAL HIDRÁULICO", candidatas);
        assertThat(a.motivo()).isEqualTo(b.motivo());
    }

    private static BudgetLine linhaExtra(String codigo, String conta, String descricao) {
        var po = new Budget(UUID.randomUUID(), UUID.randomUUID(), "b".repeat(64));
        return new BudgetLine(po, 999, 1, BudgetLineType.LINHA, codigo, conta, null, null, descricao,
                java.math.BigDecimal.ZERO.setScale(2), java.math.BigDecimal.ONE.setScale(2), null, null);
    }
}
