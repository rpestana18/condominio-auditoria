package br.com.condominioauditoria.api.contabil;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.mensagens.ResultadoProcessamento.Enriquecimento;
import br.com.condominioauditoria.api.mensagens.ResultadoProcessamento.LancamentoLido;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR 0004, Decisão 3: a chave do lançamento sobrevive ao reprocesso (id novo) e muda quando o fluxo muda. */
class ImpressaoLancamentoTest {

    private final UUID condominio = UUID.randomUUID();
    private final UUID arquivo = UUID.randomUUID();
    private final UUID fundo = UUID.randomUUID();

    @Test
    void mesmaLeituraDoMesmoArquivoTemAMesmaChaveComIdNovo() {
        Lancamento primeira = new Lancamento(condominio, arquivo, fundo, lido("1064", "250.00", 12));
        Lancamento reprocesso = new Lancamento(condominio, arquivo, fundo, lido("1064", "250.00", 12));

        assertThat(reprocesso.getId()).isNotEqualTo(primeira.getId());
        assertThat(ImpressaoLancamento.chave(reprocesso)).isEqualTo(ImpressaoLancamento.chave(primeira)).hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void valorOrdemContaOuArquivoDiferentesMudamAChave() {
        String base = ImpressaoLancamento.chave(new Lancamento(condominio, arquivo, fundo, lido("1064", "250.00", 12)));

        assertThat(ImpressaoLancamento.chave(new Lancamento(condominio, arquivo, fundo, lido("1064", "250.01", 12))))
                .isNotEqualTo(base);
        assertThat(ImpressaoLancamento.chave(new Lancamento(condominio, arquivo, fundo, lido("1064", "250.00", 13))))
                .isNotEqualTo(base);
        assertThat(ImpressaoLancamento.chave(new Lancamento(condominio, arquivo, fundo, lido("1065", "250.00", 12))))
                .isNotEqualTo(base);
        assertThat(ImpressaoLancamento.chave(new Lancamento(condominio, UUID.randomUUID(), fundo,
                lido("1064", "250.00", 12)))).isNotEqualTo(base);
    }

    @Test
    void escalaDoValorNaoMudaAChave() {
        var a = new ImpressaoLancamento(arquivo, 3, 12, LocalDate.of(2026, 9, 9), "1064", null, new BigDecimal("250.0"),
                BigDecimal.ZERO);
        var b = new ImpressaoLancamento(arquivo, 3, 12, LocalDate.of(2026, 9, 9), "1064", "", new BigDecimal("250.00"),
                new BigDecimal("0.00"));

        assertThat(a.chave()).isEqualTo(b.chave());
    }

    private static LancamentoLido lido(String conta, String valor, int ordem) {
        return new LancamentoLido(3, ordem, LocalDate.of(2026, 9, 9), conta, "CARTAO", "", "Compra no cartão",
                BigDecimal.ZERO.setScale(2), new BigDecimal(valor), BigDecimal.ZERO.setScale(2),
                new Enriquecimento(null, null, "CARTAO", false, false));
    }
}
