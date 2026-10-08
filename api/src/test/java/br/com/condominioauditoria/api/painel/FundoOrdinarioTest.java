package br.com.condominioauditoria.api.painel;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.painel.PainelService.FundoNoPeriodo;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Valores do fluxo de caixa de setembro/2026 do piloto (RF-05.1a e RF-05.1b). */
class FundoOrdinarioTest {

    private static final UUID CONDOMINIO = UUID.randomUUID();
    private static final UUID ENERGIA = UUID.randomUUID();
    private static final UUID AGUA = UUID.randomUUID();

    private static final List<FundoNoPeriodo> SETEMBRO = List.of(
            fundo(CONDOMINIO, "CONDOMÍNIO", "253802.03", "493167.58", "449455.13", "297514.48"),
            fundo(ENERGIA, "ENERGIA ELÉTRICA", "24475.84", "99615.32", "23721.24", "100369.92"),
            fundo(AGUA, "ÁGUA/ESGOTO", "25676.03", "130270.60", "144092.31", "11854.32"));

    @Test
    void fundoConfirmadoMostraOSaldoAcumuladoDele() {
        var ordinario = FundoOrdinario.de(CONDOMINIO, "CONDOMÍNIO", SETEMBRO).orElseThrow();

        assertThat(ordinario.confirmado()).isTrue();
        assertThat(ordinario.fundo()).isEqualTo("CONDOMÍNIO");
        assertThat(ordinario.saldoAtual()).isEqualByComparingTo("297514.48");
    }

    @Test
    void semConfirmacaoSugereOFundoComMaisEntradas() {
        var sugestao = FundoOrdinario.de(null, null, SETEMBRO).orElseThrow();

        assertThat(sugestao.confirmado()).isFalse();
        assertThat(sugestao.fundoId()).isEqualTo(CONDOMINIO);
        assertThat(sugestao.saldoAtual()).isEqualByComparingTo("297514.48");
    }

    @Test
    void confirmacaoValeMesmoQuandoOutroFundoTemMaisEntradas() {
        var ordinario = FundoOrdinario.de(AGUA, "ÁGUA/ESGOTO", SETEMBRO).orElseThrow();

        assertThat(ordinario.confirmado()).isTrue();
        assertThat(ordinario.saldoAtual()).isEqualByComparingTo("11854.32");
    }

    @Test
    void fundoConfirmadoForaDoRelatorioVemSemSaldo() {
        var ordinario = FundoOrdinario.de(UUID.randomUUID(), "CONDOMÍNIO", SETEMBRO).orElseThrow();

        assertThat(ordinario.confirmado()).isTrue();
        assertThat(ordinario.saldoAtual()).isNull();
    }

    @Test
    void semConfirmacaoESemEntradasNaoHaSugestao() {
        var semEntradas = List.of(fundo(CONDOMINIO, "CONDOMÍNIO", "100.00", "0.00", "0.00", "100.00"));

        assertThat(FundoOrdinario.de(null, null, semEntradas)).isEmpty();
    }

    private static FundoNoPeriodo fundo(UUID id, String nome, String anterior, String entradas, String saidas,
            String atual) {
        BigDecimal e = new BigDecimal(entradas);
        BigDecimal s = new BigDecimal(saidas);
        return new FundoNoPeriodo(id, nome, new BigDecimal(anterior), e, s, e.subtract(s), new BigDecimal(atual));
    }
}
