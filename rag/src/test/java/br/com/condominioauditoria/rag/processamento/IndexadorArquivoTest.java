package br.com.condominioauditoria.rag.processamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.storage.Storage;
import br.com.condominioauditoria.rag.indice.DocumentoCortado;
import br.com.condominioauditoria.rag.indice.EmbeddingsIndisponiveisException;
import br.com.condominioauditoria.rag.indice.GeradorEmbeddings;
import br.com.condominioauditoria.rag.indice.RepositorioIndice;
import br.com.condominioauditoria.rag.indice.RepositorioIndice.DocumentoIndexado;
import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido;
import br.com.condominioauditoria.rag.mensagens.ContratoMensagens;
import br.com.condominioauditoria.rag.mensagens.Filas;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Ollama fora na indexação: o arquivo entra na busca por palavra e o próximo pedido gera os vetores. */
class IndexadorArquivoTest {

    private static final UUID ARQUIVO = UUID.fromString("5a1c9e3b-2f4d-4b8a-8c7e-1d2f3a4b5c6d");
    private static final String SHA = "a".repeat(64);

    private final ContratoMensagens contrato = new ContratoMensagens();
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final Storage armazenamento = mock(Storage.class);
    private final LeitorDocumentosHttp leitor = mock(LeitorDocumentosHttp.class);
    private final GeradorEmbeddings embeddings = mock(GeradorEmbeddings.class);
    private final RepositorioIndice repositorio = mock(RepositorioIndice.class);
    private final IndexadorArquivo indexador = new IndexadorArquivo(contrato, rabbit, armazenamento, leitor,
            embeddings, repositorio);

    @BeforeEach
    void preparar() throws Exception {
        when(embeddings.modelo()).thenReturn("bge-m3");
        when(embeddings.aceita(any())).thenReturn(true);
        when(armazenamento.open(anyString())).thenAnswer(i -> new ByteArrayInputStream(new byte[] {1}));
        when(leitor.ler(anyString(), any())).thenReturn(new DocumentoLido("1", "t",
                new DocumentoLido.Arquivo("ata.pdf", SHA, 1), "docx", List.of(), List.of(),
                List.of(new DocumentoLido.Paragrafo(1, "Multa de 2% por atraso", null))));
    }

    @Test
    void ollamaForaGravaTrechosSemVetorEPublicaIndexadoComAviso() throws Exception {
        when(repositorio.buscar(ARQUIVO)).thenReturn(Optional.empty());
        when(embeddings.gerar(anyList())).thenThrow(new EmbeddingsIndisponiveisException("Ollama fora do ar", null));

        indexador.aoReceber(pedido());

        ArgumentCaptor<DocumentoCortado> cortado = ArgumentCaptor.forClass(DocumentoCortado.class);
        verify(repositorio).substituir(any(), cortado.capture(), isNull(), isNull(), eq(
                "Indexado só para a busca por palavra, sem busca por significado: Ollama fora do ar"));
        assertThat(cortado.getValue().trechos()).hasSize(1);
        verify(repositorio, never()).marcarErro(any(), any(), any());
        List<JsonNode> publicadas = publicadas(2);
        assertThat(publicadas.get(0).get("situacao").asString()).isEqualTo("INDEXANDO");
        assertThat(publicadas.get(1).get("situacao").asString()).isEqualTo("INDEXADO");
        assertThat(publicadas.get(1).get("modeloEmbeddings").isNull()).isTrue();
        assertThat(publicadas.get(1).get("trechos").asInt()).isEqualTo(1);
        assertThat(publicadas.get(1).get("motivo").asString()).contains("Ollama fora do ar");
    }

    @Test
    void indexadoSemVetorNaoEhRepetidoDeIndexadoComBgeM3() throws Exception {
        when(repositorio.buscar(ARQUIVO)).thenReturn(Optional.of(new DocumentoIndexado(ARQUIVO, SHA, "indexado",
                "Indexado só para a busca por palavra", 1, 1, null, "1", false)));
        when(embeddings.gerar(anyList())).thenReturn(List.of(new float[1024]));

        indexador.aoReceber(pedido());

        verify(repositorio, never()).confirmarSemReindexar(any());
        verify(repositorio).substituir(any(), any(), anyList(), eq("bge-m3"), isNull());
        assertThat(publicadas(2).get(1).get("modeloEmbeddings").asString()).isEqualTo("bge-m3");
    }

    @Test
    void indexadoComBgeM3EhRepetido() throws Exception {
        when(repositorio.buscar(ARQUIVO)).thenReturn(Optional.of(new DocumentoIndexado(ARQUIVO, SHA, "indexado",
                null, 1, 1, "bge-m3", "1", false)));

        indexador.aoReceber(pedido());

        verify(repositorio).confirmarSemReindexar(any());
        verify(repositorio, never()).substituir(any(), any(), any(), any(), any());
        assertThat(publicadas(1).getFirst().get("situacao").asString()).isEqualTo("INDEXADO");
    }

    private List<JsonNode> publicadas(int quantas) {
        ArgumentCaptor<Message> mensagens = ArgumentCaptor.forClass(Message.class);
        verify(rabbit, times(quantas)).send(eq(""), eq(Filas.RESULTADOS_INDEXACAO), mensagens.capture());
        var mapper = JsonMapper.builder().build();
        return mensagens.getAllValues().stream().map(m -> mapper.readTree(m.getBody())).toList();
    }

    private static Message pedido() throws Exception {
        byte[] json = Files.readAllBytes(Path.of(System.getProperty("contratos.dir"),
                "mensagens/v1/exemplos/indexar-arquivo-indexar.json"));
        return new Message(json, new MessageProperties());
    }
}
