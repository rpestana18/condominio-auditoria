package br.com.condominioauditoria.backend.mensagens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import br.com.condominioauditoria.backend.arquivo.Categoria;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/** O backend entende o exemplo do contrato (o mesmo que o rag produz) e só envia mensagens válidas. */
class ContratoMensagensTest {

    private final ContratoMensagens contrato = new ContratoMensagens();

    @Test
    void leOExemploDoContrato() throws Exception {
        byte[] json = Files.readAllBytes(Path.of(System.getProperty("contratos.dir"),
                "mensagens/v1/exemplos/resultado-concluido.json"));

        ResultadoProcessamento r = contrato.lerResultado(json);

        assertThat(r.situacao()).isEqualTo(ResultadoProcessamento.Situacao.CONCLUIDO);
        assertThat(r.fluxoDeCaixa().totalLancamentos()).isEqualTo(1);
        var lancamento = r.fluxoDeCaixa().secoes().getFirst().lancamentos().getFirst();
        assertThat(lancamento.debito()).isEqualByComparingTo("1500.10");
        assertThat(lancamento.debito().scale()).isEqualTo(2);
        assertThat(lancamento.enriquecimento().fornecedor()).isEqualTo("SABESP");
    }

    @Test
    void falhouSemMotivoEhRecusado() {
        String json = """
                {"versao":1,"processamentoId":"%s","arquivoId":"%s","condominioId":"%s","situacao":"FALHOU"}"""
                .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> contrato.lerResultado(json.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
    }

    @Test
    void dinheiroComoNumeroEhRecusado() throws Exception {
        String json = Files.readString(Path.of(System.getProperty("contratos.dir"),
                "mensagens/v1/exemplos/resultado-concluido.json")).replace("\"1500.10\"", "1500.1");
        assertThatThrownBy(() -> contrato.lerResultado(json.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
    }

    @Test
    void pedidoDeLeituraSaiNoContrato() {
        var arquivo = new Arquivo(UUID.randomUUID(), Categoria.BALANCETE, "fluxo.pdf", "c/BALANCETE/2026/x-fluxo.pdf",
                "c".repeat(64), 10, "application/pdf", "gestor");
        String json = new String(contrato.escrever(ArquivoRecebido.de(arquivo)), StandardCharsets.UTF_8);
        assertThat(json).contains("\"categoria\":\"BALANCETE\"").contains(arquivo.getProcessamentoId().toString());
    }

    // ---- indexação (ADR 0003, Decisão 5.1) ----

    @Test
    void leOExemploDeIndexado() throws Exception {
        ResultadoIndexacao r = contrato.lerResultadoIndexacao(exemplo("resultado-indexacao-indexado.json"));

        assertThat(r.situacao()).isEqualTo(ResultadoIndexacao.Situacao.INDEXADO);
        assertThat(r.indexacaoId()).isEqualTo(UUID.fromString("3c9d1e2f-4a5b-4c6d-8e7f-9a0b1c2d3e4f"));
        assertThat(r.paginas()).isEqualTo(12);
        assertThat(r.trechos()).isEqualTo(15);
        assertThat(r.versaoIndexador()).isEqualTo("1");
    }

    @Test
    void leOExemploDeSemTexto() throws Exception {
        ResultadoIndexacao r = contrato.lerResultadoIndexacao(exemplo("resultado-indexacao-sem-texto.json"));

        assertThat(r.situacao()).isEqualTo(ResultadoIndexacao.Situacao.SEM_TEXTO);
        assertThat(r.motivo()).contains("sem texto extraível");
        assertThat(r.trechos()).isZero();
    }

    @Test
    void indexadoSemContagensEhRecusado() {
        String json = """
                {"versao":1,"indexacaoId":"%s","arquivoId":"%s","condominioId":"%s","situacao":"INDEXADO"}"""
                .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> contrato.lerResultadoIndexacao(json.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
    }

    @Test
    void erroDeIndexacaoSemMotivoEhRecusado() {
        String json = """
                {"versao":1,"indexacaoId":"%s","arquivoId":"%s","condominioId":"%s","situacao":"ERRO","motivo":""}"""
                .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> contrato.lerResultadoIndexacao(json.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
    }

    @Test
    void indexandoMinimoEhAceito() {
        String json = """
                {"versao":1,"indexacaoId":"%s","arquivoId":"%s","condominioId":"%s","situacao":"INDEXANDO"}"""
                .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThat(contrato.lerResultadoIndexacao(json.getBytes(StandardCharsets.UTF_8)).situacao())
                .isEqualTo(ResultadoIndexacao.Situacao.INDEXANDO);
    }

    /** Os exemplos de pedido do contrato cabem no registro do backend e saem iguais, válidos. */
    @ParameterizedTest
    @ValueSource(strings = {"indexar-arquivo-indexar.json", "indexar-arquivo-retirar.json"})
    void exemploDePedidoDeIndexacaoFazIdaEVolta(String nome) throws Exception {
        var mapper = JsonMapper.builder().build();
        IndexarArquivo pedido = mapper.readValue(exemplo(nome), IndexarArquivo.class);

        String json = new String(contrato.escrever(pedido), StandardCharsets.UTF_8);

        assertThat(mapper.readTree(json)).isEqualTo(mapper.readTree(exemplo(nome)));
    }

    @Test
    void pedidoDeIndexacaoSaiNoContrato() {
        var arquivo = new Arquivo(UUID.randomUUID(), Categoria.ATA, "ata.pdf", "c/ATA/2026/x-ata.pdf",
                "a".repeat(64), 10, "application/pdf", "gestor");
        String json = new String(contrato.escrever(IndexarArquivo.indexar(arquivo)), StandardCharsets.UTF_8);
        assertThat(json).contains("\"operacao\":\"INDEXAR\"").contains("\"categoria\":\"ATA\"")
                .contains(arquivo.getIndexacaoId().toString());
        // Cada pedido tem o seu id, diferente do id da leitura
        assertThat(arquivo.getIndexacaoId()).isNotEqualTo(arquivo.getProcessamentoId());
    }

    @Test
    void pedidoForaDoContratoNaoSai() {
        var arquivo = new Arquivo(UUID.randomUUID(), Categoria.ATA, "ata.pdf", "c/ATA/2026/x-ata.pdf",
                "hash-invalido", 10, "application/pdf", "gestor");
        assertThatThrownBy(() -> contrato.escrever(IndexarArquivo.indexar(arquivo)))
                .hasMessageContaining("fora do contrato");
    }

    private static byte[] exemplo(String nome) throws Exception {
        return Files.readAllBytes(Path.of(System.getProperty("contratos.dir"), "mensagens/v1/exemplos", nome));
    }
}
