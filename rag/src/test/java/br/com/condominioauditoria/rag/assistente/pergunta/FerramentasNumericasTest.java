package br.com.condominioauditoria.rag.assistente.pergunta;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.rag.assistente.pergunta.DadoConsultado.Linha;
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
 * Montagem do bloco "Nos dados gravados" (RF-04.13, RF-04.14): dinheiro vem do texto decimal exato do contrato de
 * consulta e sai em reais no padrão brasileiro, com os centavos como vieram. Nenhuma conta passa por double.
 */
class FerramentasNumericasTest {

    private static final String CONDOMINIO = "6f1d2c1e-3b4a-4c8e-9a51-2815a0000001";

    private final FerramentasNumericas ferramentas = new FerramentasNumericas();
    private final ConsultaFalsa backend = new ConsultaFalsa();
    private Server servidor;
    private ManagedChannel canal;
    private ClienteConsulta cliente;

    @BeforeEach
    void subir() throws Exception {
        String nome = InProcessServerBuilder.generateName();
        servidor = InProcessServerBuilder.forName(nome).directExecutor()
                .addService(ServerInterceptors.intercept(backend, backend.registrandoToken())).build().start();
        canal = InProcessChannelBuilder.forName(nome).directExecutor().build();
        cliente = new ClienteConsulta(canal, 5);
    }

    @AfterEach
    void descer() {
        canal.shutdownNow();
        servidor.shutdownNow();
    }

    @Test
    void asQuatroFerramentasEstaoOferecidasAoModelo() {
        assertThat(ferramentas.definicoes()).extracting("nome").containsExactly(
                "resumo_fundos", "buscar_lancamentos", "listar_arquivos", "conferencias_do_arquivo");
        assertThat(ferramentas.definicoes()).allSatisfy(d -> {
            assertThat(d.descricao()).isNotBlank();
            assertThat(d.esquemaEntrada()).containsKeys("properties", "required");
        });
    }

    @Test
    void resumoDeFundosSaiEmReaisComOsCentavosExatos() {
        DadoConsultado dado = ferramentas.executar("c1", "resumo_fundos", Map.of(), CONDOMINIO,
                cliente.comToken("Bearer token-do-usuario"));

        assertThat(dado.chamadaId()).isEqualTo("c1");
        assertThat(dado.consulta()).isEqualTo("resumo_fundos");
        assertThat(dado.linhas()).contains(
                new Linha("Arquivo", "fluxo-setembro.pdf"),
                new Linha("Período", "2026-09-01 a 2026-09-30"),
                new Linha("Saldo anterior", "R$ 1.000.000,07"),
                new Linha("Entradas", "R$ 250.000,03"),
                new Linha("Saídas", "R$ 125.000,01"),
                new Linha("Saldo atual", "R$ 1.125.000,09"),
                new Linha("Conferências com falha", "1"),
                new Linha("Fundo ORDINARIO",
                        "saldo anterior R$ 0,01; entradas R$ 0,02; saídas R$ 0,00; saldo atual R$ 0,03"));
        assertThat(backend.tokensRecebidos).containsExactly("Bearer token-do-usuario");
    }

    @Test
    void totaisDosLancamentosSaoSomadosComBigDecimal() {
        DadoConsultado dado = ferramentas.executar("c2", "buscar_lancamentos",
                Map.of("dataInicio", "2026-09-01", "dataFim", "2026-09-30", "somenteSaidas", true, "limite", 10),
                CONDOMINIO, cliente.comToken("Bearer t"));

        assertThat(dado.parametros()).containsExactly(
                new DadoConsultado.Parametro("dataInicio", "2026-09-01"),
                new DadoConsultado.Parametro("dataFim", "2026-09-30"),
                new DadoConsultado.Parametro("somenteSaidas", "sim"),
                new DadoConsultado.Parametro("limite", "10"));
        // 1234,56 + 0,45 = 1.235,01 exato (nenhum arredondamento de ponto flutuante)
        assertThat(dado.linhas()).startsWith(
                new Linha("Lançamentos encontrados", "3"),
                new Linha("Total das entradas", "R$ 500,00"),
                new Linha("Total das saídas", "R$ 1.235,01"),
                new Linha("Média das saídas por lançamento", "R$ 411,67"));
        assertThat(dado.linhas()).contains(
                new Linha("2026-09-03 — ORDINARIO — Energia elétrica — Enel", "R$ 1.234,56 (saída)"),
                new Linha("2026-09-20 — ORDINARIO — Taxa condominial", "R$ 500,00 (entrada)"));
    }

    @Test
    void listaDeArquivosTrazOIdParaAProximaFerramenta() {
        DadoConsultado dado = ferramentas.executar("c3", "listar_arquivos", Map.of("categoria", "BALANCETE"),
                CONDOMINIO, cliente.comToken("Bearer t"));

        assertThat(dado.linhas()).containsExactly(
                new Linha("Arquivos encontrados", "1"),
                new Linha("fluxo-setembro.pdf",
                        "BALANCETE; CONCLUIDO; período 2026-09-01 a 2026-09-30; arquivoId arq-1"));
    }

    @Test
    void conferenciasDizemOQueNaoFechou() {
        DadoConsultado dado = ferramentas.executar("c4", "conferencias_do_arquivo", Map.of("arquivoId", "arq-1"),
                CONDOMINIO, cliente.comToken("Bearer t"));

        assertThat(dado.linhas()).containsExactly(
                new Linha("Conferências", "2 ao todo, 1 sem fechar"),
                new Linha("F1 — Soma das entradas", "fechou"),
                new Linha("F2 — Saldo final", "não fechou; diferença de 0,02"));
    }

    @Test
    void oResultadoMandadoAoModeloTrazOChamadaIdEProibeCopiarNumero() {
        DadoConsultado dado = ferramentas.executar("c1", "resumo_fundos", Map.of(), CONDOMINIO,
                cliente.comToken("Bearer t"));

        String texto = FerramentasNumericas.paraOModelo(dado);
        assertThat(texto).contains("chamadaId: c1").contains("R$ 1.125.000,09")
                .contains("Não os copie na resposta");
    }

    @Test
    void reaisFormataNegativoEZeroSemPontoFlutuante() {
        assertThat(Reais.formatar("-1234.56")).isEqualTo("-R$ 1.234,56");
        assertThat(Reais.formatar("0.00")).isEqualTo("R$ 0,00");
        assertThat(Reais.formatar("")).isEqualTo("R$ 0,00");
        assertThat(Reais.valor("0.07").add(Reais.valor("0.03"))).isEqualTo(new BigDecimal("0.10"));
        assertThat(Reais.formatar(List.of(Reais.valor("0.07"), Reais.valor("0.03")).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add))).isEqualTo("R$ 0,10");
    }
}
