package br.com.condominioauditoria.rag.mensagens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.rag.dominio.fluxo.ConferenciaFluxo;
import br.com.condominioauditoria.rag.dominio.fluxo.FluxoDeCaixa;
import br.com.condominioauditoria.rag.dominio.fluxo.LancamentoFluxo;
import br.com.condominioauditoria.rag.dominio.fluxo.PosicaoFundo;
import br.com.condominioauditoria.rag.dominio.fluxo.SecaoFundo;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** As mensagens que o rag produz e consome seguem contracts/mensagens/v1. */
class ContratoMensagensTest {

    private final ContratoMensagens contrato = new ContratoMensagens();

    private static final ArquivoRecebido ARQUIVO = new ArquivoRecebido(1, UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), "BALANCETE", "fluxo.pdf", "c/BALANCETE/2026/abc-fluxo.pdf", "a".repeat(64));

    @Test
    void resultadoComFluxoSaiNoContratoComDinheiroComoTexto() {
        var lancamento = new LancamentoFluxo(1, 1, LocalDate.of(2026, 9, 2), "3.1.01", "Água", "123", "CONTA DE ÁGUA",
                BigDecimal.ZERO.setScale(2), new BigDecimal("1500.10"), new BigDecimal("8499.90"),
                new LancamentoFluxo.Enriquecimento(null, "SABESP", null, false));
        var secao = new SecaoFundo("ORDINÁRIO", new BigDecimal("10000.00"), List.of(lancamento),
                BigDecimal.ZERO.setScale(2), new BigDecimal("1500.10"));
        var posicao = new PosicaoFundo("ORDINÁRIO", new BigDecimal("10000.00"), BigDecimal.ZERO.setScale(2),
                new BigDecimal("1500.10"), new BigDecimal("8499.90"));
        var fluxo = new FluxoDeCaixa("MIO", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), List.of(secao),
                List.of(posicao), posicao);

        byte[] json = contrato.escrever(ResultadoProcessamento.concluido(ARQUIVO, "fluxo-caixa-protest", 3, fluxo,
                ConferenciaFluxo.conferir(fluxo)));

        String texto = new String(json, StandardCharsets.UTF_8);
        assertThat(texto).contains("\"debito\":\"1500.10\"").contains("\"situacao\":\"CONCLUIDO\"")
                .doesNotContain("totalLancamentos");
    }

    @Test
    void iniciadoEFalhou() {
        assertThat(contrato.escrever(ResultadoProcessamento.iniciado(ARQUIVO))).isNotEmpty();
        assertThat(contrato.escrever(ResultadoProcessamento.falhou(ARQUIVO, "leitor fora do ar"))).isNotEmpty();
    }

    @Test
    void arquivoRecebidoForaDoContratoEhRecusado() {
        String json = """
                {"versao":1,"arquivoId":"%s"}""".formatted(UUID.randomUUID());
        assertThatThrownBy(() -> contrato.lerArquivoRecebido(json.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
    }

    @Test
    void arquivoRecebidoValido() {
        String json = """
                {"versao":1,"processamentoId":"%s","arquivoId":"%s","condominioId":"%s","categoria":"BALANCETE",
                 "nomeOriginal":"fluxo.pdf","caminho":"x/y.pdf","sha256":"%s"}"""
                .formatted(UUID.randomUUID(), ARQUIVO.arquivoId(), UUID.randomUUID(), "b".repeat(64));
        assertThat(contrato.lerArquivoRecebido(json.getBytes(StandardCharsets.UTF_8)).arquivoId())
                .isEqualTo(ARQUIVO.arquivoId());
    }

    /** O exemplo do contrato, que o backend usa no teste dele, é igual ao que o rag produz para o mesmo fluxo. */
    @Test
    void exemploDoContratoEhOQueORagProduz() throws Exception {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var exemplo = mapper.readTree(java.nio.file.Files.readString(java.nio.file.Path.of(
                System.getProperty("contratos.dir"), "mensagens/v1/exemplos/resultado-concluido.json")));
        var lancamento = new LancamentoFluxo(1, 1, LocalDate.of(2026, 9, 2), "3101", "ÁGUA", "123", "CONTA DE ÁGUA",
                new BigDecimal("0.00"), new BigDecimal("1500.10"), new BigDecimal("8499.90"),
                new LancamentoFluxo.Enriquecimento(null, "SABESP", null, false));
        var secao = new SecaoFundo("ORDINÁRIO", new BigDecimal("10000.00"), List.of(lancamento),
                new BigDecimal("0.00"), new BigDecimal("1500.10"));
        var posicao = new PosicaoFundo("ORDINÁRIO", new BigDecimal("10000.00"), new BigDecimal("0.00"),
                new BigDecimal("1500.10"), new BigDecimal("8499.90"));
        var total = new PosicaoFundo("TOTAL", new BigDecimal("10000.00"), new BigDecimal("0.00"),
                new BigDecimal("1500.10"), new BigDecimal("8499.90"));
        var fluxo = new FluxoDeCaixa("EXEMPLO", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), List.of(secao),
                List.of(posicao), total);
        var arquivo = new ArquivoRecebido(1, UUID.fromString(exemplo.get("processamentoId").asString()),
                UUID.fromString(exemplo.get("arquivoId").asString()),
                UUID.fromString(exemplo.get("condominioId").asString()), "BALANCETE", "f.pdf", "x/f.pdf", "a".repeat(64));
        var conferencia = new br.com.condominioauditoria.rag.dominio.fluxo.Verificacao("SALDO_CORRENTE",
                "Saldo linha a linha em todos os fundos", true, "1 lançamentos conferidos");

        byte[] produzido = contrato.escrever(ResultadoProcessamento.concluido(arquivo, "fluxo-caixa-protest", 1, fluxo,
                List.of(conferencia)));

        assertThat(mapper.readTree(produzido)).isEqualTo(exemplo);
    }
}
