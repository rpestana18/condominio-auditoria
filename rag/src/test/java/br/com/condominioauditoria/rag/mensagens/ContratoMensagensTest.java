package br.com.condominioauditoria.rag.mensagens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.rag.dominio.fluxo.ConferenciaFluxo;
import br.com.condominioauditoria.rag.dominio.fluxo.FluxoDeCaixa;
import br.com.condominioauditoria.rag.dominio.fluxo.LancamentoFluxo;
import br.com.condominioauditoria.rag.dominio.fluxo.PosicaoFundo;
import br.com.condominioauditoria.rag.dominio.fluxo.SecaoFundo;
import br.com.condominioauditoria.rag.dominio.fluxo.Verificacao;
import br.com.condominioauditoria.rag.dominio.po.PrevisaoOrcamentaria;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** O rag consome ArquivoRecebido v1 e publica ResultadoProcessamento v2 (contracts/mensagens). */
class ContratoMensagensTest {

    private final ContratoMensagens contrato = new ContratoMensagens();

    private static final ArquivoRecebido ARQUIVO = new ArquivoRecebido(1, UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), "BALANCETE", "fluxo.pdf", "c/BALANCETE/2026/abc-fluxo.pdf", "a".repeat(64));

    @Test
    void resultadoComFluxoSaiNoContratoComDinheiroComoTexto() {
        var lancamento = new LancamentoFluxo(1, 1, LocalDate.of(2026, 9, 2), "3.1.01", "Água", "123", "CONTA DE ÁGUA",
                BigDecimal.ZERO.setScale(2), new BigDecimal("1500.10"), new BigDecimal("8499.90"),
                new LancamentoFluxo.Enriquecimento(null, "SABESP", null, false, false));
        var secao = new SecaoFundo("ORDINÁRIO", new BigDecimal("10000.00"), List.of(lancamento),
                BigDecimal.ZERO.setScale(2), new BigDecimal("1500.10"));
        var posicao = new PosicaoFundo("ORDINÁRIO", new BigDecimal("10000.00"), BigDecimal.ZERO.setScale(2),
                new BigDecimal("1500.10"), new BigDecimal("8499.90"));
        var fluxo = new FluxoDeCaixa("MIO", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), List.of(secao),
                List.of(posicao), posicao);

        byte[] json = contrato.escrever(ResultadoProcessamento.concluido(ARQUIVO, "fluxo-caixa-protest", 3, fluxo,
                ConferenciaFluxo.conferir(fluxo)));

        String texto = new String(json, StandardCharsets.UTF_8);
        assertThat(texto).contains("\"versao\":2").contains("\"debito\":\"1500.10\"")
                .contains("\"situacao\":\"CONCLUIDO\"").contains("\"recebimentoCota\":false")
                .contains("\"previsaoOrcamentaria\":null").doesNotContain("totalLancamentos");
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

    /** O exemplo do contrato (fluxo com recebimento de cota), que o backend usa no teste dele, é o que o rag produz. */
    @Test
    void exemploDeFluxoDoContratoEhOQueORagProduz() throws Exception {
        var exemplo = MAPPER.readTree(exemplo("resultado-concluido-fluxo.json"));
        var agua = new LancamentoFluxo(1, 1, LocalDate.of(2026, 9, 2), "3101", "ÁGUA", "123", "CONTA DE ÁGUA",
                new BigDecimal("0.00"), new BigDecimal("1500.10"), new BigDecimal("8499.90"),
                new LancamentoFluxo.Enriquecimento(null, "SABESP", null, false, false));
        var cota = new LancamentoFluxo(1, 2, LocalDate.of(2026, 9, 10), null, null, null, "RECIBOS ACUMULADOS",
                new BigDecimal("14260.79"), new BigDecimal("0.00"), new BigDecimal("64260.79"),
                new LancamentoFluxo.Enriquecimento(null, null, null, false, true));
        var ordinario = new SecaoFundo("ORDINÁRIO", new BigDecimal("10000.00"), List.of(agua),
                new BigDecimal("0.00"), new BigDecimal("1500.10"));
        var reserva = new SecaoFundo("FUNDO DE RESERVA", new BigDecimal("50000.00"), List.of(cota),
                new BigDecimal("14260.79"), new BigDecimal("0.00"));
        var posicoes = List.of(
                new PosicaoFundo("ORDINÁRIO", new BigDecimal("10000.00"), new BigDecimal("0.00"),
                        new BigDecimal("1500.10"), new BigDecimal("8499.90")),
                new PosicaoFundo("FUNDO DE RESERVA", new BigDecimal("50000.00"), new BigDecimal("14260.79"),
                        new BigDecimal("0.00"), new BigDecimal("64260.79")));
        var total = new PosicaoFundo("TOTAL", new BigDecimal("60000.00"), new BigDecimal("14260.79"),
                new BigDecimal("1500.10"), new BigDecimal("72760.69"));
        var fluxo = new FluxoDeCaixa("EXEMPLO", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                List.of(ordinario, reserva), posicoes, total);
        var conferencia = new Verificacao("SALDO_CORRENTE", "Saldo linha a linha em todos os fundos", true,
                "2 lançamentos conferidos");

        byte[] produzido = contrato.escrever(ResultadoProcessamento.concluido(arquivoDo(exemplo), "fluxo-caixa-protest",
                1, fluxo, List.of(conferencia)));

        assertThat(MAPPER.readTree(produzido)).isEqualTo(exemplo);
    }

    /**
     * O exemplo da PO lida cabe, campo a campo, nos tipos do rag (rag.dominio.po) e volta igual: nenhum campo do
     * contrato fica sem lugar e nenhum campo a mais é publicado. A leitura da PO em si é o passo 3 da ADR 0004.
     */
    @Test
    void exemploDaPoDoContratoCabeNosTiposDoRag() throws Exception {
        String json = exemplo("resultado-concluido-po.json");
        var exemplo = MAPPER.readTree(json);
        var lido = MAPPER.readValue(json, ResultadoProcessamento.class);

        PrevisaoOrcamentaria po = lido.previsaoOrcamentaria();
        assertThat(po.linhas()).extracting(PrevisaoOrcamentaria.LinhaPo::codigoImpresso).containsSubsequence("1.3.2",
                "1.3.2");
        var ferias = po.linhas().stream().filter(l -> l.codigoImpresso().equals("1.1.5")).findFirst().orElseThrow();
        assertThat(ferias.orcado()).isEqualByComparingTo("1585.14");
        assertThat(ferias.orcado().scale()).isEqualTo(2);

        byte[] produzido = contrato.escrever(ResultadoProcessamento.concluidoPo(arquivoDo(exemplo), lido.interpretador(),
                lido.paginas(), po, lido.conferencias()));

        assertThat(MAPPER.readTree(produzido)).isEqualTo(exemplo);
    }

    private static final tools.jackson.databind.json.JsonMapper MAPPER =
            tools.jackson.databind.json.JsonMapper.builder().build();

    private static String exemplo(String nome) throws Exception {
        return java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("contratos.dir"),
                "mensagens/v2/exemplos", nome));
    }

    private static ArquivoRecebido arquivoDo(tools.jackson.databind.JsonNode exemplo) {
        return new ArquivoRecebido(1, UUID.fromString(exemplo.get("processamentoId").asString()),
                UUID.fromString(exemplo.get("arquivoId").asString()),
                UUID.fromString(exemplo.get("condominioId").asString()), "BALANCETE", "f.pdf", "x/f.pdf", "a".repeat(64));
    }
}
