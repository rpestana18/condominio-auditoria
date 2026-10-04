package br.com.condominioauditoria.rag.mensagens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Mensagens da indexação (rag.indexacao e backend.indexacao) seguem contracts/mensagens/v1, com os exemplos do contrato. */
class ContratoIndexacaoTest {

    private final ContratoMensagens contrato = new ContratoMensagens();
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void exemploIndexarEhLidoPeloRag() throws Exception {
        IndexarArquivo pedido = contrato.lerIndexarArquivo(exemplo("indexar-arquivo-indexar.json"));

        assertThat(pedido.operacao()).isEqualTo(IndexarArquivo.Operacao.INDEXAR);
        assertThat(pedido.arquivoId()).isEqualTo(UUID.fromString("5a1c9e3b-2f4d-4b8a-8c7e-1d2f3a4b5c6d"));
        assertThat(pedido.categoria()).isEqualTo("ATA");
        assertThat(pedido.competenciaInicio()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(pedido.competenciaFim()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(pedido.versaoArquivo()).isEqualTo(1);
        assertThat(pedido.modoEmbeddings()).isNull();
        assertThat(pedido.comVetores()).isTrue(); // ausente = LOCAL
    }

    @Test
    void exemploRetirarEhLidoPeloRag() throws Exception {
        IndexarArquivo pedido = contrato.lerIndexarArquivo(exemplo("indexar-arquivo-retirar.json"));

        assertThat(pedido.operacao()).isEqualTo(IndexarArquivo.Operacao.RETIRAR);
        assertThat(pedido.competenciaInicio()).isNull();
        assertThat(pedido.versaoArquivo()).isNull();
    }

    @Test
    void embeddingsDesligadosNaoGeramVetores() {
        String json = """
                {"versao":1,"operacao":"INDEXAR","indexacaoId":"%s","arquivoId":"%s","condominioId":"%s",
                 "categoria":"ATA","nomeOriginal":"a.pdf","caminho":"x/a.pdf","sha256":"%s",
                 "modoEmbeddings":"DESLIGADO"}""".formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "c".repeat(64));
        assertThat(contrato.lerIndexarArquivo(json.getBytes(StandardCharsets.UTF_8)).comVetores()).isFalse();
    }

    @Test
    void indexarForaDoContratoEhRecusado() {
        String json = """
                {"versao":1,"operacao":"APAGAR","arquivoId":"%s"}""".formatted(UUID.randomUUID());
        assertThatThrownBy(() -> contrato.lerIndexarArquivo(json.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
    }

    /** O exemplo do contrato, que o backend usa no teste dele, é igual ao que o rag produz. */
    @Test
    void indexadoProduzidoIgualAoExemplo() throws Exception {
        IndexarArquivo pedido = contrato.lerIndexarArquivo(exemplo("indexar-arquivo-indexar.json"));

        byte[] produzido = contrato.escrever(ResultadoIndexacao.indexado(pedido, 12, 15, "bge-m3", "1"));

        assertThat(mapper.readTree(produzido)).isEqualTo(arvore("resultado-indexacao-indexado.json"));
    }

    @Test
    void semTextoProduzidoIgualAoExemplo() throws Exception {
        IndexarArquivo pedido = contrato.lerIndexarArquivo(exemplo("indexar-arquivo-indexar.json"));

        byte[] produzido = contrato.escrever(ResultadoIndexacao.semTexto(pedido,
                "PDF sem texto extraível (provavelmente digitalizado); nenhuma das 12 páginas tem texto", 12, "1"));

        assertThat(mapper.readTree(produzido)).isEqualTo(arvore("resultado-indexacao-sem-texto.json"));
    }

    @Test
    void indexandoRetiradoEErroSaemNoContrato() throws Exception {
        IndexarArquivo pedido = contrato.lerIndexarArquivo(exemplo("indexar-arquivo-indexar.json"));

        assertThat(texto(contrato.escrever(ResultadoIndexacao.indexando(pedido)))).contains("\"INDEXANDO\"");
        assertThat(texto(contrato.escrever(ResultadoIndexacao.retirado(pedido)))).contains("\"RETIRADO\"");
        assertThat(texto(contrato.escrever(ResultadoIndexacao.erro(pedido, "Ollama fora do ar"))))
                .contains("\"ERRO\"").contains("Ollama fora do ar");
    }

    @Test
    void indexadoSemVetoresComAvisoSaiNoContrato() throws Exception {
        IndexarArquivo pedido = contrato.lerIndexarArquivo(exemplo("indexar-arquivo-indexar.json"));
        String json = texto(contrato.escrever(ResultadoIndexacao.indexado(pedido, 3, 2, null, "1",
                "Indexado só para a busca por palavra, sem busca por significado: Ollama fora")));
        assertThat(json).contains("\"INDEXADO\"").contains("\"modeloEmbeddings\":null").contains("Ollama fora");
    }

    @Test
    void erroSemMotivoEhRecusadoNaSaida() throws Exception {
        IndexarArquivo pedido = contrato.lerIndexarArquivo(exemplo("indexar-arquivo-indexar.json"));
        assertThatThrownBy(() -> contrato.escrever(ResultadoIndexacao.erro(pedido, null)))
                .hasMessageContaining("fora do contrato");
    }

    private static byte[] exemplo(String nome) throws Exception {
        return Files.readAllBytes(Path.of(System.getProperty("contratos.dir"), "mensagens/v1/exemplos", nome));
    }

    private JsonNode arvore(String nome) throws Exception {
        return mapper.readTree(exemplo(nome));
    }

    private static String texto(byte[] json) {
        return new String(json, StandardCharsets.UTF_8);
    }
}
