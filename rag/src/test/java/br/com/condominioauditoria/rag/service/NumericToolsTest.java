package br.com.condominioauditoria.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.rag.client.QueryClient;
import br.com.condominioauditoria.rag.dto.QueriedData;
import br.com.condominioauditoria.rag.dto.QueriedData.Row;
import br.com.condominioauditoria.rag.util.ReaisFormatter;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Building of the "Nos dados gravados" block (RF-04.13, RF-04.14): money comes from the exact decimal text of the
 * query contract and goes out in reais in the Brazilian format, with the cents as they came. No calculation goes
 * through double.
 */
class NumericToolsTest {

    private static final String CONDOMINIUM = "6f1d2c1e-3b4a-4c8e-9a51-2815a0000001";

    private final NumericTools tools = new NumericTools();
    private final FakeQuery api = new FakeQuery();
    private Server server;
    private ManagedChannel channel;
    private QueryClient client;

    @BeforeEach
    void start() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(ServerInterceptors.intercept(api, api.recordingToken())).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        client = new QueryClient(channel, 5);
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void theFourToolsAreOfferedToModel() {
        assertThat(tools.definitions()).extracting("name").containsExactly(
                "resumo_fundos", "buscar_lancamentos", "listar_arquivos", "conferencias_do_arquivo");
        assertThat(tools.definitions()).allSatisfy(d -> {
            assertThat(d.description()).isNotBlank();
            assertThat(d.inputSchema()).containsKeys("properties", "required");
        });
    }

    @Test
    void fundSummaryComesInReaisWithExactCents() {
        QueriedData dataItem = tools.execute("c1", "resumo_fundos", Map.of(), CONDOMINIUM,
                client.withToken("Bearer token-do-usuario"));

        assertThat(dataItem.callId()).isEqualTo("c1");
        assertThat(dataItem.query()).isEqualTo("resumo_fundos");
        assertThat(dataItem.rows()).contains(
                new Row("Arquivo", "fluxo-setembro.pdf"),
                new Row("Período", "2026-09-01 a 2026-09-30"),
                new Row("Saldo anterior", "R$ 1.000.000,07"),
                new Row("Entradas", "R$ 250.000,03"),
                new Row("Saídas", "R$ 125.000,01"),
                new Row("Saldo atual", "R$ 1.125.000,09"),
                new Row("Conferências com falha", "1"),
                new Row("Fundo ORDINARIO",
                        "saldo anterior R$ 0,01; entradas R$ 0,02; saídas R$ 0,00; saldo atual R$ 0,03"));
        assertThat(api.receivedTokens).containsExactly("Bearer token-do-usuario");
    }

    @Test
    void entryTotalsAreSummedWithBigDecimal() {
        QueriedData dataItem = tools.execute("c2", "buscar_lancamentos",
                Map.of("dataInicio", "2026-09-01", "dataFim", "2026-09-30", "somenteSaidas", true, "limite", 10),
                CONDOMINIUM, client.withToken("Bearer t"));

        assertThat(dataItem.params()).containsExactly(
                new QueriedData.Param("dataInicio", "2026-09-01"),
                new QueriedData.Param("dataFim", "2026-09-30"),
                new QueriedData.Param("somenteSaidas", "sim"),
                new QueriedData.Param("limite", "10"));
        // 1234,56 + 0,45 = 1.235,01 exact (no floating point rounding)
        assertThat(dataItem.rows()).startsWith(
                new Row("Lançamentos encontrados", "3"),
                new Row("Total das entradas", "R$ 500,00"),
                new Row("Total das saídas", "R$ 1.235,01"),
                new Row("Média das saídas por lançamento", "R$ 411,67"));
        assertThat(dataItem.rows()).contains(
                new Row("2026-09-03 — ORDINARIO — Energia elétrica — Enel", "R$ 1.234,56 (saída)"),
                new Row("2026-09-20 — ORDINARIO — Taxa condominial", "R$ 500,00 (entrada)"));
    }

    @Test
    void fileListBringsIdForNextTool() {
        QueriedData dataItem = tools.execute("c3", "listar_arquivos", Map.of("categoria", "BALANCETE"),
                CONDOMINIUM, client.withToken("Bearer t"));

        assertThat(dataItem.rows()).containsExactly(
                new Row("Arquivos encontrados", "1"),
                new Row("fluxo-setembro.pdf",
                        "BALANCETE; CONCLUIDO; período 2026-09-01 a 2026-09-30; arquivoId arq-1"));
    }

    @Test
    void checksSayWhatDidNotAddUp() {
        QueriedData dataItem = tools.execute("c4", "conferencias_do_arquivo", Map.of("arquivoId", "arq-1"),
                CONDOMINIUM, client.withToken("Bearer t"));

        assertThat(dataItem.rows()).containsExactly(
                new Row("Conferências", "2 ao todo, 1 sem fechar"),
                new Row("F1 — Soma das entradas", "fechou"),
                new Row("F2 — Saldo final", "não fechou; diferença de 0,02"));
    }

    @Test
    void resultSentToModelCarriesCallIdAndForbidsCopyingNumbers() {
        QueriedData dataItem = tools.execute("c1", "resumo_fundos", Map.of(), CONDOMINIUM,
                client.withToken("Bearer t"));

        String text = NumericTools.forModel(dataItem);
        assertThat(text).contains("chamadaId: c1").contains("R$ 1.125.000,09")
                .contains("Não os copie na resposta");
    }

    @Test
    void reaisFormatsNegativeAndZeroWithoutFloatingPoint() {
        assertThat(ReaisFormatter.format("-1234.56")).isEqualTo("-R$ 1.234,56");
        assertThat(ReaisFormatter.format("0.00")).isEqualTo("R$ 0,00");
        assertThat(ReaisFormatter.format("")).isEqualTo("R$ 0,00");
        assertThat(ReaisFormatter.parse("0.07").add(ReaisFormatter.parse("0.03"))).isEqualTo(new BigDecimal("0.10"));
        assertThat(ReaisFormatter.format(List.of(ReaisFormatter.parse("0.07"), ReaisFormatter.parse("0.03")).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add))).isEqualTo("R$ 0,10");
    }
}
