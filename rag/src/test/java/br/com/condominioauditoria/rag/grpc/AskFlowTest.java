package br.com.condominioauditoria.rag.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.contratos.assistente.v1.AssistenteGrpc;
import br.com.condominioauditoria.contratos.assistente.v1.ConfiguracaoPergunta;
import br.com.condominioauditoria.contratos.assistente.v1.EtapaPergunta;
import br.com.condominioauditoria.contratos.assistente.v1.ListarProvedoresRequest;
import br.com.condominioauditoria.contratos.assistente.v1.ModoBusca;
import br.com.condominioauditoria.contratos.assistente.v1.PerguntarEvento;
import br.com.condominioauditoria.contratos.assistente.v1.PerguntarRequest;
import br.com.condominioauditoria.contratos.assistente.v1.RespostaPergunta;
import br.com.condominioauditoria.contratos.assistente.v1.SituacaoResposta;
import br.com.condominioauditoria.contratos.assistente.v1.UsoProvedor;
import br.com.condominioauditoria.rag.client.AiGateway;
import br.com.condominioauditoria.rag.client.QueryClient;
import br.com.condominioauditoria.rag.config.properties.AiProperties;
import br.com.condominioauditoria.rag.config.properties.RagProperties;
import br.com.condominioauditoria.rag.search.DocumentSearch;
import br.com.condominioauditoria.rag.search.EmbeddingGenerator;
import br.com.condominioauditoria.rag.search.FoundChunk;
import br.com.condominioauditoria.rag.search.Location;
import br.com.condominioauditoria.rag.security.RagKeys;
import br.com.condominioauditoria.rag.service.AssistantInstructions;
import br.com.condominioauditoria.rag.service.FakeQuery;
import br.com.condominioauditoria.rag.service.NumericTools;
import br.com.condominioauditoria.rag.service.ProviderCatalog;
import br.com.condominioauditoria.rag.service.QuestionService;
import br.com.condominioauditoria.rag.service.ResponseValidator;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Perguntar flow end to end, with a fake HTTP server in place of Claude and a fake gRPC server in place of the api's
 * Consulta: happy path with one tool, invalid answer twice becoming NOT_FOUND, model safety refusal and key rejected
 * by the provider (401) becoming PERMISSION_DENIED.
 */
public class AskFlowTest {

    private static final String CONDOMINIUM = "6f1d2c1e-3b4a-4c8e-9a51-2815a0000001";
    private static final String TOKEN = "Bearer token-do-usuario";
    private static final String MARK = AssistantInstructions.UNVERIFIED_MARK;
    private static final UUID CHUNK = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static KeyPair keyPair;

    private FakeClaude claude;
    private FakeQuery query;
    private Server queryServer;
    private ManagedChannel queryChannel;
    private Server server;
    private ManagedChannel channel;
    private AssistenteGrpc.AssistenteBlockingStub client;
    private DocumentSearch search;

    @BeforeEach
    public void start() throws Exception {
        if (keyPair == null) {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            keyPair = generator.generateKeyPair();
        }
        claude = new FakeClaude();
        query = new FakeQuery();
        String queryName = InProcessServerBuilder.generateName();
        queryServer = InProcessServerBuilder.forName(queryName).directExecutor()
                .addService(ServerInterceptors.intercept(query, query.recordingToken())).build().start();
        queryChannel = InProcessChannelBuilder.forName(queryName).directExecutor().build();

        search = mock(DocumentSearch.class);
        when(search.search(any(), anyString(), any(), anyInt())).thenReturn(new DocumentSearch.Result(
                List.of(new FoundChunk(CHUNK, UUID.randomUUID(), "contrato.pdf", "CONTRACT",
                        new Location.Page(3),
                        "A taxa de administração contratada é de R$ 1.234,56 por mês.", 1.0, "a".repeat(64))),
                DocumentSearch.Mode.HYBRID));
        var embeddings = mock(EmbeddingGenerator.class);
        when(embeddings.accepts(anyString())).thenReturn(true);
        when(embeddings.model()).thenReturn("bge-m3");

        var properties = properties(claude.url());
        var service = new QuestionService(search, catalog(), new RagKeys(keyPair.getPrivate(), keyPair.getPublic()),
                new AiGateway(properties), new NumericTools(), new QueryClient(queryChannel, 5),
                new ResponseValidator(List.of("desvio", "fraude", "roubo", "culpa")), properties);

        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(ServerInterceptors.intercept(
                        new AssistantGrpcService(search, embeddings, service, catalog(),
                                new RagKeys(keyPair.getPrivate(), keyPair.getPublic())),
                        new GrpcAuthorization()))
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        var headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), TOKEN);
        client = AssistenteGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    @AfterEach
    public void stop() {
        channel.shutdownNow();
        server.shutdownNow();
        queryChannel.shutdownNow();
        queryServer.shutdownNow();
        claude.close();
    }

    @Test
    public void happyPathWithOneToolBuildsBothBlocks() {
        claude.respond(FakeClaude.withTool("toolu_1", "resumo_fundos", "{}", 500, 40));
        claude.respond(FakeClaude.withText("""
                {"nosDocumentos":[{"texto":"O contrato registra taxa de administração de R$ 1.234,56 por mês \
                %s.","trechoIds":["%s"]}],
                 "nosDadosGravados":[{"chamadaId":"c1","comentario":"O saldo do fundo está no fluxo mais recente."}],
                 "naoEncontrado":false,"sugestao":""}""".formatted(MARK, CHUNK), 800, 120));

        var events = events(request("Qual é a taxa de administração e o saldo?"));

        assertThat(stages(events)).containsSubsequence(
                EtapaPergunta.ETAPA_PERGUNTA_BUSCANDO_TRECHOS,
                EtapaPergunta.ETAPA_PERGUNTA_REDIGINDO,
                EtapaPergunta.ETAPA_PERGUNTA_CONSULTANDO_DADOS,
                EtapaPergunta.ETAPA_PERGUNTA_VALIDANDO);
        RespostaPergunta response = last(events);
        assertThat(response.getSituacao()).isEqualTo(SituacaoResposta.SITUACAO_RESPOSTA_RESPONDIDA);
        assertThat(response.getNosDocumentosList()).singleElement().satisfies(p -> {
            assertThat(p.getTexto()).contains("R$ 1.234,56").contains(MARK);
            assertThat(p.getTrechoIdsList()).containsExactly(CHUNK.toString());
        });
        assertThat(response.getTrechosCitadosList()).singleElement()
                .satisfies(t -> assertThat(t.getNomeArquivo()).isEqualTo("contrato.pdf"));
        // The "nos dados gravados" block is built by the rag from the tool, not from the model's text
        assertThat(response.getNosDadosGravadosList()).singleElement().satisfies(d -> {
            assertThat(d.getChamadaId()).isEqualTo("c1");
            assertThat(d.getConsulta()).isEqualTo("resumo_fundos");
            assertThat(d.getComentario()).doesNotContainPattern("\\d");
            assertThat(d.getLinhasList()).anySatisfy(l -> {
                assertThat(l.getRotulo()).isEqualTo("Saldo atual");
                assertThat(l.getValor()).isEqualTo("R$ 1.125.000,09");
            });
        });
        // Usage summed over the two calls to the provider
        assertThat(response.getUso().getTokensEntrada()).isEqualTo(1300);
        assertThat(response.getUso().getTokensSaida()).isEqualTo(160);
        assertThat(response.getUso().getTentativas()).isEqualTo(1);
        assertThat(response.getUso().getVersaoPrompt()).isEqualTo(AssistantInstructions.VERSION);
        assertThat(response.getUso().getProvedor()).isEqualTo("anthropic");
        assertThat(response.getUso().getModelo()).isEqualTo("claude-sonnet-5-5");
        // The tool ran in the api with the SAME token as the request
        assertThat(query.receivedTokens).containsExactly(TOKEN);
        // The request to the provider carries the output schema, the effort, the four tools and the condominium's key
        assertThat(claude.receivedKeys).containsOnly("sk-ant-chave-do-condominio");
        assertThat(claude.requests.getFirst())
                .contains("\"output_config\"").contains("\"effort\":\"medium\"")
                .contains("\"resumo_fundos\"").contains("\"buscar_lancamentos\"")
                .contains("\"listar_arquivos\"").contains("\"conferencias_do_arquivo\"")
                .contains("nosDadosGravados").contains(CHUNK.toString())
                .doesNotContain("\"thinking\"").doesNotContain("\"tool_choice\"");
    }

    @Test
    public void invalidAnswerTwiceBecomesNotFound() {
        String invalid = """
                {"nosDocumentos":[{"texto":"Houve desvio de recursos no fundo.","trechoIds":["%s"]}],
                 "nosDadosGravados":[],"naoEncontrado":false,"sugestao":""}""".formatted(CHUNK);
        claude.respond(FakeClaude.withText(invalid, 100, 10));
        claude.respond(FakeClaude.withText(invalid, 100, 10));

        var events = events(request("O síndico desviou dinheiro?"));

        assertThat(stages(events)).contains(EtapaPergunta.ETAPA_PERGUNTA_NOVA_TENTATIVA);
        RespostaPergunta response = last(events);
        assertThat(response.getSituacao()).isEqualTo(SituacaoResposta.SITUACAO_RESPOSTA_NAO_ENCONTRADA);
        assertThat(response.getNosDocumentosList()).isEmpty();
        assertThat(response.getNosDadosGravadosList()).isEmpty();
        assertThat(response.getTrechosCitadosList()).isEmpty();
        assertThat(response.getUso().getTentativas()).isEqualTo(2);
        // The second call carries the reason for the rejection
        assertThat(claude.requests).hasSize(2);
        assertThat(claude.requests.get(1)).contains("fora de aspas de citação literal");
    }

    @Test
    public void modelSafetyRefusalBecomesNotFoundWithWarningAndNoRetry() {
        claude.respond(FakeClaude.withRefusal());

        RespostaPergunta response = last(events(request("pergunta qualquer")));

        assertThat(response.getSituacao()).isEqualTo(SituacaoResposta.SITUACAO_RESPOSTA_NAO_ENCONTRADA);
        assertThat(response.getAviso()).contains("recusou responder");
        assertThat(claude.requests).hasSize(1);
        assertThat(response.getUso().getTentativas()).isEqualTo(1);
    }

    @Test
    public void keyRejectedByProviderBecomesPermissionDenied() {
        claude.respondStatus(401, FakeClaude.error("authentication_error", "invalid x-api-key"));

        assertThatThrownBy(() -> events(request("qual o saldo?")))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(error -> {
                    var status = ((StatusRuntimeException) error).getStatus();
                    assertThat(status.getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
                    assertThat(status.getDescription())
                            .isEqualTo("A chave de API do condomínio foi recusada pelo provedor.");
                    assertThat(status.getDescription()).doesNotContain("sk-ant");
                });
    }

    @Test
    public void providerLimitBecomesResourceExhaustedAndServerErrorBecomesUnavailable() {
        claude.respondStatus(429, FakeClaude.error("rate_limit_error", "too many requests"));

        assertThatThrownBy(() -> events(request("qual o saldo?")))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(error -> assertThat(((StatusRuntimeException) error).getStatus().getCode())
                        .isEqualTo(Status.Code.RESOURCE_EXHAUSTED));
    }

    @Test
    public void noTokenInMetadataIsRejectedBeforeAnyCall() {
        var withoutToken = AssistenteGrpc.newBlockingStub(channel);

        assertThatThrownBy(() -> withoutToken.perguntar(request("qual o saldo?")).next())
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(error -> assertThat(((StatusRuntimeException) error).getStatus().getCode())
                        .isEqualTo(Status.Code.UNAUTHENTICATED));
        assertThat(claude.requests).isEmpty();
    }

    @Test
    public void emptyAndOverLimitQuestionsAreInvalidArgument() {
        assertThatThrownBy(() -> events(request("   ")))
                .satisfies(error -> assertThat(((StatusRuntimeException) error).getStatus().getCode())
                        .isEqualTo(Status.Code.INVALID_ARGUMENT));
        assertThatThrownBy(() -> events(request("a".repeat(2001))))
                .satisfies(error -> assertThat(((StatusRuntimeException) error).getStatus().getCode())
                        .isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    @Test
    public void noEncryptedKeyOrModelNotInCatalogIsFailedPrecondition() {
        var withoutKey = request("qual o saldo?").toBuilder()
                .setConfiguracao(configuration().toBuilder().clearChaveCifrada()).build();
        assertThatThrownBy(() -> events(withoutKey))
                .satisfies(error -> assertThat(((StatusRuntimeException) error).getStatus().getCode())
                        .isEqualTo(Status.Code.FAILED_PRECONDITION));

        var otherModel = request("qual o saldo?").toBuilder()
                .setConfiguracao(configuration().toBuilder().setModelo("claude-inexistente")).build();
        assertThatThrownBy(() -> events(otherModel))
                .satisfies(error -> assertThat(((StatusRuntimeException) error).getStatus().getDescription())
                        .contains("não está no catálogo"));

        var embeddingsProvider = request("qual o saldo?").toBuilder()
                .setConfiguracao(configuration().toBuilder().setProvedor("ollama-local").setModelo("bge-m3")).build();
        assertThatThrownBy(() -> events(embeddingsProvider))
                .satisfies(error -> assertThat(((StatusRuntimeException) error).getStatus().getDescription())
                        .contains("não serve para redigir respostas"));
    }

    @Test
    public void undecryptableKeyIsFailedPreconditionWithoutTechnicalDetail() {
        var tampered = request("qual o saldo?").toBuilder()
                .setConfiguracao(configuration().toBuilder()
                        .setChaveCifrada(ByteString.copyFrom(new byte[] { 1, 0, 8, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
                            12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31 })))
                .build();

        assertThatThrownBy(() -> events(tampered))
                .satisfies(error -> {
                    var status = ((StatusRuntimeException) error).getStatus();
                    assertThat(status.getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
                    assertThat(status.getDescription()).isEqualTo(
                            "A chave de IA deste condomínio não pôde ser lida; cadastre de novo.");
                });
    }

    @Test
    public void providerCatalogComesWithPublicPemKey() {
        var response = client.listarProvedores(ListarProvedoresRequest.getDefaultInstance());

        assertThat(response.getChavePublicaPem()).startsWith("-----BEGIN PUBLIC KEY-----");
        assertThat(response.getProvedoresList()).extracting("codigo").containsExactly("anthropic", "ollama-local");
        var anthropic = response.getProvedores(0);
        assertThat(anthropic.getUso()).isEqualTo(UsoProvedor.USO_PROVEDOR_RESPOSTAS);
        assertThat(anthropic.getPrecisaChave()).isTrue();
        assertThat(anthropic.getLocal()).isFalse();
        assertThat(anthropic.getModelosList()).extracting("id", "padrao", "precoEntradaMilhaoUsd",
                "precoSaidaMilhaoUsd")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("claude-sonnet-5-5", true, "2.00", "10.00"),
                        org.assertj.core.groups.Tuple.tuple("claude-haiku-4-5", false, "1.00", "5.00"));
        var ollama = response.getProvedores(1);
        assertThat(ollama.getUso()).isEqualTo(UsoProvedor.USO_PROVEDOR_EMBEDDINGS);
        assertThat(ollama.getLocal()).isTrue();
        assertThat(ollama.getDimensao()).isEqualTo(1024);
        assertThat(ollama.getModelos(0).getPrecoEntradaMilhaoUsd()).isEqualTo("0");
    }

    @Test
    public void listProvidersWithoutTokenIsRejected() {
        var withoutToken = AssistenteGrpc.newBlockingStub(channel);

        assertThatThrownBy(() -> withoutToken.listarProvedores(ListarProvedoresRequest.getDefaultInstance()))
                .satisfies(error -> assertThat(((StatusRuntimeException) error).getStatus().getCode())
                        .isEqualTo(Status.Code.UNAUTHENTICATED));
    }

    @Test
    public void searchFallingBackToKeywordWarnsUser() {
        when(search.search(any(), anyString(), any(), anyInt())).thenReturn(new DocumentSearch.Result(
                List.of(), DocumentSearch.Mode.KEYWORD));
        claude.respond(FakeClaude.withText(
                "{\"nosDocumentos\":[],\"nosDadosGravados\":[],\"naoEncontrado\":true,"
                        + "\"sugestao\":\"não há extrato de setembro de 2026 enviado\"}", 50, 5));

        RespostaPergunta response = last(events(request("qual o saldo?")));

        assertThat(response.getSituacao()).isEqualTo(SituacaoResposta.SITUACAO_RESPOSTA_NAO_ENCONTRADA);
        assertThat(response.getAviso()).contains("busca por significado está indisponível");
        assertThat(response.getSugestao()).isEqualTo("não há extrato de setembro de 2026 enviado");
    }

    // -----------------------------------------------------------------------------------------------------------

    private List<PerguntarEvento> events(PerguntarRequest request) {
        List<PerguntarEvento> events = new ArrayList<>();
        Iterator<PerguntarEvento> stream = client.perguntar(request);
        while (stream.hasNext()) {
            events.add(stream.next());
        }
        return events;
    }

    private static List<EtapaPergunta> stages(List<PerguntarEvento> events) {
        return events.stream().filter(PerguntarEvento::hasAndamento).map(e -> e.getAndamento().getEtapa()).toList();
    }

    private static RespostaPergunta last(List<PerguntarEvento> events) {
        assertThat(events.getLast().hasResposta()).isTrue();
        return events.getLast().getResposta();
    }

    private static PerguntarRequest request(String question) {
        return PerguntarRequest.newBuilder()
                .setCondominioId(CONDOMINIUM)
                .setPergunta(question)
                .setConfiguracao(configuration())
                .build();
    }

    private static ConfiguracaoPergunta configuration() {
        return ConfiguracaoPergunta.newBuilder()
                .setProvedor("anthropic")
                .setModelo("claude-sonnet-5-5")
                .setModoBusca(ModoBusca.MODO_BUSCA_HIBRIDA)
                .setChaveCifrada(ByteString.copyFrom(encrypt("sk-ant-chave-do-condominio", keyPair.getPublic())))
                .build();
    }

    private static ProviderCatalog catalog() {
        return new ProviderCatalog(new AiProperties(List.of(
                new AiProperties.AiProvider("anthropic", "Anthropic (Claude)", "anthropic",
                        AiProperties.AiFunction.ANSWERS, false, true, 0, List.of(
                                new AiProperties.AiModel("claude-sonnet-5-5", "Claude Sonnet 5.5", true, "2.00",
                                        "10.00"),
                                new AiProperties.AiModel("claude-haiku-4-5", "Claude Haiku 4.5", false, "1.00",
                                        "5.00"))),
                new AiProperties.AiProvider("ollama-local", "Ollama local", "ollama",
                        AiProperties.AiFunction.EMBEDDINGS, true, false, 1024, List.of(
                                new AiProperties.AiModel("bge-m3", "BGE-M3", true, "0", "0"))))));
    }

    private static RagProperties properties(String claudeUrl) {
        var assistant = new RagProperties.Assistant("", false, claudeUrl, 20, 0, 16000, "medium", 6, 10,
                "localhost:9090", 5, List.of("desvio", "fraude", "roubo", "culpa"));
        return new RagProperties(null, null, null, null, null, assistant);
    }

    /** Same envelope as the api (assistente.proto); here only so the test has a really encrypted key. */
    private static byte[] encrypt(String key, PublicKey publicKey) {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            SecretKey aes = generator.generateKey();
            Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
            rsa.init(Cipher.ENCRYPT_MODE, publicKey, new javax.crypto.spec.OAEPParameterSpec("SHA-256", "MGF1",
                    java.security.spec.MGF1ParameterSpec.SHA256, javax.crypto.spec.PSource.PSpecified.DEFAULT));
            byte[] encryptedKey = rsa.doFinal(aes.getEncoded());
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);
            Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
            gcm.init(Cipher.ENCRYPT_MODE, aes, new GCMParameterSpec(128, nonce));
            byte[] encrypted = gcm.doFinal(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] envelope = new byte[3 + encryptedKey.length + 12 + encrypted.length];
            envelope[0] = 1;
            envelope[1] = (byte) (encryptedKey.length >> 8);
            envelope[2] = (byte) encryptedKey.length;
            System.arraycopy(encryptedKey, 0, envelope, 3, encryptedKey.length);
            System.arraycopy(nonce, 0, envelope, 3 + encryptedKey.length, 12);
            System.arraycopy(encrypted, 0, envelope, 3 + encryptedKey.length + 12, encrypted.length);
            return envelope;
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
