package br.com.condominioauditoria.rag.messaging;

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

/**
 * Indexing messages (rag.indexacao and backend.indexacao) follow contracts/mensagens/v1, with the contract examples.
 */
public class IndexingContractTest {

    private final MessageContract contract = new MessageContract();
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    public void indexExampleIsReadByRag() throws Exception {
        IndexFileMessage request = contract.readIndexFile(example("indexar-arquivo-indexar.json"));

        assertThat(request.operation()).isEqualTo(IndexFileMessage.Operation.INDEXAR);
        assertThat(request.fileId()).isEqualTo(UUID.fromString("5a1c9e3b-2f4d-4b8a-8c7e-1d2f3a4b5c6d"));
        assertThat(request.category()).isEqualTo("ATA");
        assertThat(request.periodStart()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(request.periodEnd()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(request.fileVersion()).isEqualTo(1);
        assertThat(request.embeddingMode()).isNull();
        assertThat(request.withVectors()).isTrue(); // assertThat(request.withVectors()).isTrue(); // missing = LOCAL
    }

    @Test
    public void withdrawExampleIsReadByRag() throws Exception {
        IndexFileMessage request = contract.readIndexFile(example("indexar-arquivo-retirar.json"));

        assertThat(request.operation()).isEqualTo(IndexFileMessage.Operation.RETIRAR);
        assertThat(request.periodStart()).isNull();
        assertThat(request.fileVersion()).isNull();
    }

    @Test
    public void embeddingsOffProduceNoVectors() {
        String json = """
                {"versao":1,"operacao":"INDEXAR","indexacaoId":"%s","arquivoId":"%s","condominioId":"%s",
                 "categoria":"ATA","nomeOriginal":"a.pdf","caminho":"x/a.pdf","sha256":"%s",
                 "modoEmbeddings":"DESLIGADO"}""".formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "c".repeat(64));
        assertThat(contract.readIndexFile(json.getBytes(StandardCharsets.UTF_8)).withVectors()).isFalse();
    }

    @Test
    public void indexOutsideContractIsRejected() {
        String json = """
                {"versao":1,"operacao":"APAGAR","arquivoId":"%s"}""".formatted(UUID.randomUUID());
        assertThatThrownBy(() -> contract.readIndexFile(json.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
    }

    /** The contract example, which the api uses in its own test, equals what the rag produces. */
    @Test
    public void producedIndexedEqualsExample() throws Exception {
        IndexFileMessage request = contract.readIndexFile(example("indexar-arquivo-indexar.json"));

        byte[] produced = contract.write(IndexingResultMessage.indexed(request, 12, 15, "bge-m3", "1"));

        assertThat(mapper.readTree(produced)).isEqualTo(tree("resultado-indexacao-indexado.json"));
    }

    @Test
    public void producedNoTextEqualsExample() throws Exception {
        IndexFileMessage request = contract.readIndexFile(example("indexar-arquivo-indexar.json"));

        byte[] produced = contract.write(IndexingResultMessage.noText(request,
                "PDF sem texto extraível (provavelmente digitalizado); nenhuma das 12 páginas tem texto", 12, "1"));

        assertThat(mapper.readTree(produced)).isEqualTo(tree("resultado-indexacao-sem-texto.json"));
    }

    @Test
    public void indexingWithdrawnAndErrorFollowContract() throws Exception {
        IndexFileMessage request = contract.readIndexFile(example("indexar-arquivo-indexar.json"));

        assertThat(text(contract.write(IndexingResultMessage.indexing(request)))).contains("\"INDEXANDO\"");
        assertThat(text(contract.write(IndexingResultMessage.withdrawn(request)))).contains("\"RETIRADO\"");
        assertThat(text(contract.write(IndexingResultMessage.error(request, "Ollama fora do ar"))))
                .contains("\"ERRO\"").contains("Ollama fora do ar");
    }

    @Test
    public void indexedWithoutVectorsWithWarningFollowsContract() throws Exception {
        IndexFileMessage request = contract.readIndexFile(example("indexar-arquivo-indexar.json"));
        String json = text(contract.write(IndexingResultMessage.indexed(request, 3, 2, null, "1",
                "Indexado só para a busca por palavra, sem busca por significado: Ollama fora")));
        assertThat(json).contains("\"INDEXADO\"").contains("\"modeloEmbeddings\":null").contains("Ollama fora");
    }

    @Test
    public void errorWithoutReasonIsRejectedOnOutput() throws Exception {
        IndexFileMessage request = contract.readIndexFile(example("indexar-arquivo-indexar.json"));
        assertThatThrownBy(() -> contract.write(IndexingResultMessage.error(request, null)))
                .hasMessageContaining("fora do contrato");
    }

    private static byte[] example(String name) throws Exception {
        return Files.readAllBytes(Path.of(System.getProperty("contratos.dir"), "mensagens/v1/exemplos", name));
    }

    private JsonNode tree(String name) throws Exception {
        return mapper.readTree(example(name));
    }

    private static String text(byte[] json) {
        return new String(json, StandardCharsets.UTF_8);
    }
}
