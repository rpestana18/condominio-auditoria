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
import br.com.condominioauditoria.rag.assistente.catalogo.CatalogoProvedores;
import br.com.condominioauditoria.rag.assistente.catalogo.PropriedadesIa;
import br.com.condominioauditoria.rag.assistente.chave.ChavesRag;
import br.com.condominioauditoria.rag.assistente.gateway.GatewayIa;
import br.com.condominioauditoria.rag.assistente.pergunta.ClienteConsulta;
import br.com.condominioauditoria.rag.assistente.pergunta.ConsultaFalsa;
import br.com.condominioauditoria.rag.assistente.pergunta.FerramentasNumericas;
import br.com.condominioauditoria.rag.assistente.pergunta.InstrucoesAssistente;
import br.com.condominioauditoria.rag.assistente.pergunta.ServicoPergunta;
import br.com.condominioauditoria.rag.assistente.pergunta.ValidadorResposta;
import br.com.condominioauditoria.rag.config.PropriedadesRag;
import br.com.condominioauditoria.rag.indice.BuscaDocumentos;
import br.com.condominioauditoria.rag.indice.GeradorEmbeddings;
import br.com.condominioauditoria.rag.indice.Localizacao;
import br.com.condominioauditoria.rag.indice.RepositorioIndice;
import br.com.condominioauditoria.rag.indice.TrechoEncontrado;
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
 * Fluxo Perguntar de ponta a ponta, com um servidor HTTP falso no lugar do Claude e um servidor gRPC falso no lugar
 * do Consulta do backend: caso feliz com uma ferramenta, resposta inválida duas vezes virando NAO_ENCONTRADA, recusa
 * de segurança do modelo e chave recusada pelo provedor (401) virando PERMISSION_DENIED.
 */
class PerguntarFluxoTest {

    private static final String CONDOMINIO = "6f1d2c1e-3b4a-4c8e-9a51-2815a0000001";
    private static final String TOKEN = "Bearer token-do-usuario";
    private static final String MARCA = InstrucoesAssistente.MARCA_NAO_CONFERIDO;
    private static final UUID TRECHO = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static KeyPair par;

    private ClaudeFalso claude;
    private ConsultaFalsa consulta;
    private Server servidorConsulta;
    private ManagedChannel canalConsulta;
    private Server servidor;
    private ManagedChannel canal;
    private AssistenteGrpc.AssistenteBlockingStub cliente;
    private BuscaDocumentos busca;

    @BeforeEach
    void subir() throws Exception {
        if (par == null) {
            var gerador = KeyPairGenerator.getInstance("RSA");
            gerador.initialize(3072);
            par = gerador.generateKeyPair();
        }
        claude = new ClaudeFalso();
        consulta = new ConsultaFalsa();
        String nomeConsulta = InProcessServerBuilder.generateName();
        servidorConsulta = InProcessServerBuilder.forName(nomeConsulta).directExecutor()
                .addService(ServerInterceptors.intercept(consulta, consulta.registrandoToken())).build().start();
        canalConsulta = InProcessChannelBuilder.forName(nomeConsulta).directExecutor().build();

        busca = mock(BuscaDocumentos.class);
        when(busca.buscar(any(), anyString(), any(), anyInt())).thenReturn(new BuscaDocumentos.Resultado(
                List.of(new TrechoEncontrado(TRECHO, UUID.randomUUID(), "contrato.pdf", "CONTRATO",
                        new Localizacao.Pagina(3),
                        "A taxa de administração contratada é de R$ 1.234,56 por mês.", 1.0, "a".repeat(64))),
                BuscaDocumentos.Modo.HIBRIDA));
        var embeddings = mock(GeradorEmbeddings.class);
        when(embeddings.aceita(anyString())).thenReturn(true);
        when(embeddings.modelo()).thenReturn("bge-m3");

        var propriedades = propriedades(claude.url());
        var servico = new ServicoPergunta(busca, catalogo(), new ChavesRag(par.getPrivate(), par.getPublic()),
                new GatewayIa(propriedades), new FerramentasNumericas(), new ClienteConsulta(canalConsulta, 5),
                new ValidadorResposta(List.of("desvio", "fraude", "roubo", "culpa")), propriedades);

        String nome = InProcessServerBuilder.generateName();
        servidor = InProcessServerBuilder.forName(nome).directExecutor()
                .addService(ServerInterceptors.intercept(
                        new AssistenteGrpcServico(busca, embeddings, servico, catalogo(),
                                new ChavesRag(par.getPrivate(), par.getPublic())),
                        new AutorizacaoGrpc()))
                .build().start();
        canal = InProcessChannelBuilder.forName(nome).directExecutor().build();
        var cabecalhos = new Metadata();
        cabecalhos.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), TOKEN);
        cliente = AssistenteGrpc.newBlockingStub(canal)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(cabecalhos));
    }

    @AfterEach
    void descer() {
        canal.shutdownNow();
        servidor.shutdownNow();
        canalConsulta.shutdownNow();
        servidorConsulta.shutdownNow();
        claude.close();
    }

    @Test
    void casoFelizComUmaFerramentaMontaOsDoisBlocos() {
        claude.responde(ClaudeFalso.comFerramenta("toolu_1", "resumo_fundos", "{}", 500, 40));
        claude.responde(ClaudeFalso.comTexto("""
                {"nosDocumentos":[{"texto":"O contrato registra taxa de administração de R$ 1.234,56 por mês \
                %s.","trechoIds":["%s"]}],
                 "nosDadosGravados":[{"chamadaId":"c1","comentario":"O saldo do fundo está no fluxo mais recente."}],
                 "naoEncontrado":false,"sugestao":""}""".formatted(MARCA, TRECHO), 800, 120));

        var eventos = eventos(pedido("Qual é a taxa de administração e o saldo?"));

        assertThat(etapas(eventos)).containsSubsequence(
                EtapaPergunta.ETAPA_PERGUNTA_BUSCANDO_TRECHOS,
                EtapaPergunta.ETAPA_PERGUNTA_REDIGINDO,
                EtapaPergunta.ETAPA_PERGUNTA_CONSULTANDO_DADOS,
                EtapaPergunta.ETAPA_PERGUNTA_VALIDANDO);
        RespostaPergunta resposta = ultima(eventos);
        assertThat(resposta.getSituacao()).isEqualTo(SituacaoResposta.SITUACAO_RESPOSTA_RESPONDIDA);
        assertThat(resposta.getNosDocumentosList()).singleElement().satisfies(p -> {
            assertThat(p.getTexto()).contains("R$ 1.234,56").contains(MARCA);
            assertThat(p.getTrechoIdsList()).containsExactly(TRECHO.toString());
        });
        assertThat(resposta.getTrechosCitadosList()).singleElement()
                .satisfies(t -> assertThat(t.getNomeArquivo()).isEqualTo("contrato.pdf"));
        // O bloco "nos dados gravados" é montado pelo rag a partir da ferramenta, não do texto do modelo
        assertThat(resposta.getNosDadosGravadosList()).singleElement().satisfies(d -> {
            assertThat(d.getChamadaId()).isEqualTo("c1");
            assertThat(d.getConsulta()).isEqualTo("resumo_fundos");
            assertThat(d.getComentario()).doesNotContainPattern("\\d");
            assertThat(d.getLinhasList()).anySatisfy(l -> {
                assertThat(l.getRotulo()).isEqualTo("Saldo atual");
                assertThat(l.getValor()).isEqualTo("R$ 1.125.000,09");
            });
        });
        // Uso somado nas duas chamadas ao provedor
        assertThat(resposta.getUso().getTokensEntrada()).isEqualTo(1300);
        assertThat(resposta.getUso().getTokensSaida()).isEqualTo(160);
        assertThat(resposta.getUso().getTentativas()).isEqualTo(1);
        assertThat(resposta.getUso().getVersaoPrompt()).isEqualTo(InstrucoesAssistente.VERSAO);
        assertThat(resposta.getUso().getProvedor()).isEqualTo("anthropic");
        assertThat(resposta.getUso().getModelo()).isEqualTo("claude-sonnet-5-5");
        // A ferramenta rodou no backend com o MESMO token do pedido
        assertThat(consulta.tokensRecebidos).containsExactly(TOKEN);
        // O pedido ao provedor leva o esquema de saída, o esforço e as quatro ferramentas, e a chave do condomínio
        assertThat(claude.chavesRecebidas).containsOnly("sk-ant-chave-do-condominio");
        assertThat(claude.pedidos.getFirst())
                .contains("\"output_config\"").contains("\"effort\":\"medium\"")
                .contains("\"resumo_fundos\"").contains("\"buscar_lancamentos\"")
                .contains("\"listar_arquivos\"").contains("\"conferencias_do_arquivo\"")
                .contains("nosDadosGravados").contains(TRECHO.toString())
                .doesNotContain("\"thinking\"").doesNotContain("\"tool_choice\"");
    }

    @Test
    void respostaInvalidaDuasVezesViraNaoEncontrada() {
        String invalida = """
                {"nosDocumentos":[{"texto":"Houve desvio de recursos no fundo.","trechoIds":["%s"]}],
                 "nosDadosGravados":[],"naoEncontrado":false,"sugestao":""}""".formatted(TRECHO);
        claude.responde(ClaudeFalso.comTexto(invalida, 100, 10));
        claude.responde(ClaudeFalso.comTexto(invalida, 100, 10));

        var eventos = eventos(pedido("O síndico desviou dinheiro?"));

        assertThat(etapas(eventos)).contains(EtapaPergunta.ETAPA_PERGUNTA_NOVA_TENTATIVA);
        RespostaPergunta resposta = ultima(eventos);
        assertThat(resposta.getSituacao()).isEqualTo(SituacaoResposta.SITUACAO_RESPOSTA_NAO_ENCONTRADA);
        assertThat(resposta.getNosDocumentosList()).isEmpty();
        assertThat(resposta.getNosDadosGravadosList()).isEmpty();
        assertThat(resposta.getTrechosCitadosList()).isEmpty();
        assertThat(resposta.getUso().getTentativas()).isEqualTo(2);
        // A segunda chamada leva o motivo da recusa
        assertThat(claude.pedidos).hasSize(2);
        assertThat(claude.pedidos.get(1)).contains("fora de aspas de citação literal");
    }

    @Test
    void recusaDeSegurancaDoModeloViraNaoEncontradaComAvisoESemNovaTentativa() {
        claude.responde(ClaudeFalso.comRecusa());

        RespostaPergunta resposta = ultima(eventos(pedido("pergunta qualquer")));

        assertThat(resposta.getSituacao()).isEqualTo(SituacaoResposta.SITUACAO_RESPOSTA_NAO_ENCONTRADA);
        assertThat(resposta.getAviso()).contains("recusou responder");
        assertThat(claude.pedidos).hasSize(1);
        assertThat(resposta.getUso().getTentativas()).isEqualTo(1);
    }

    @Test
    void chaveRecusadaPeloProvedorViraPermissionDenied() {
        claude.respondeStatus(401, ClaudeFalso.erro("authentication_error", "invalid x-api-key"));

        assertThatThrownBy(() -> eventos(pedido("qual o saldo?")))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(erro -> {
                    var status = ((StatusRuntimeException) erro).getStatus();
                    assertThat(status.getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
                    assertThat(status.getDescription())
                            .isEqualTo("A chave de API do condomínio foi recusada pelo provedor.");
                    assertThat(status.getDescription()).doesNotContain("sk-ant");
                });
    }

    @Test
    void limiteDoProvedorViraResourceExhaustedEErroDeServidorViraUnavailable() {
        claude.respondeStatus(429, ClaudeFalso.erro("rate_limit_error", "too many requests"));

        assertThatThrownBy(() -> eventos(pedido("qual o saldo?")))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(erro -> assertThat(((StatusRuntimeException) erro).getStatus().getCode())
                        .isEqualTo(Status.Code.RESOURCE_EXHAUSTED));
    }

    @Test
    void semTokenNoMetadadoERecusadoAntesDeQualquerChamada() {
        var semToken = AssistenteGrpc.newBlockingStub(canal);

        assertThatThrownBy(() -> semToken.perguntar(pedido("qual o saldo?")).next())
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(erro -> assertThat(((StatusRuntimeException) erro).getStatus().getCode())
                        .isEqualTo(Status.Code.UNAUTHENTICATED));
        assertThat(claude.pedidos).isEmpty();
    }

    @Test
    void perguntaVaziaEAcimaDoLimiteSaoInvalidArgument() {
        assertThatThrownBy(() -> eventos(pedido("   ")))
                .satisfies(erro -> assertThat(((StatusRuntimeException) erro).getStatus().getCode())
                        .isEqualTo(Status.Code.INVALID_ARGUMENT));
        assertThatThrownBy(() -> eventos(pedido("a".repeat(2001))))
                .satisfies(erro -> assertThat(((StatusRuntimeException) erro).getStatus().getCode())
                        .isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    @Test
    void semChaveCifradaOuModeloForaDoCatalogoEFailedPrecondition() {
        var semChave = pedido("qual o saldo?").toBuilder()
                .setConfiguracao(configuracao().toBuilder().clearChaveCifrada()).build();
        assertThatThrownBy(() -> eventos(semChave))
                .satisfies(erro -> assertThat(((StatusRuntimeException) erro).getStatus().getCode())
                        .isEqualTo(Status.Code.FAILED_PRECONDITION));

        var outroModelo = pedido("qual o saldo?").toBuilder()
                .setConfiguracao(configuracao().toBuilder().setModelo("claude-inexistente")).build();
        assertThatThrownBy(() -> eventos(outroModelo))
                .satisfies(erro -> assertThat(((StatusRuntimeException) erro).getStatus().getDescription())
                        .contains("não está no catálogo"));

        var provedorDeEmbeddings = pedido("qual o saldo?").toBuilder()
                .setConfiguracao(configuracao().toBuilder().setProvedor("ollama-local").setModelo("bge-m3")).build();
        assertThatThrownBy(() -> eventos(provedorDeEmbeddings))
                .satisfies(erro -> assertThat(((StatusRuntimeException) erro).getStatus().getDescription())
                        .contains("não serve para redigir respostas"));
    }

    @Test
    void chaveQueNaoDecifraEFailedPreconditionSemDetalheTecnico() {
        var adulterada = pedido("qual o saldo?").toBuilder()
                .setConfiguracao(configuracao().toBuilder()
                        .setChaveCifrada(ByteString.copyFrom(new byte[] { 1, 0, 8, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
                            12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31 })))
                .build();

        assertThatThrownBy(() -> eventos(adulterada))
                .satisfies(erro -> {
                    var status = ((StatusRuntimeException) erro).getStatus();
                    assertThat(status.getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
                    assertThat(status.getDescription()).isEqualTo(
                            "A chave de IA deste condomínio não pôde ser lida; cadastre de novo.");
                });
    }

    @Test
    void catalogoDeProvedoresSaiComAChavePublicaPem() {
        var resposta = cliente.listarProvedores(ListarProvedoresRequest.getDefaultInstance());

        assertThat(resposta.getChavePublicaPem()).startsWith("-----BEGIN PUBLIC KEY-----");
        assertThat(resposta.getProvedoresList()).extracting("codigo").containsExactly("anthropic", "ollama-local");
        var anthropic = resposta.getProvedores(0);
        assertThat(anthropic.getUso()).isEqualTo(UsoProvedor.USO_PROVEDOR_RESPOSTAS);
        assertThat(anthropic.getPrecisaChave()).isTrue();
        assertThat(anthropic.getLocal()).isFalse();
        assertThat(anthropic.getModelosList()).extracting("id", "padrao", "precoEntradaMilhaoUsd",
                "precoSaidaMilhaoUsd")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("claude-sonnet-5-5", true, "2.00", "10.00"),
                        org.assertj.core.groups.Tuple.tuple("claude-haiku-4-5", false, "1.00", "5.00"));
        var ollama = resposta.getProvedores(1);
        assertThat(ollama.getUso()).isEqualTo(UsoProvedor.USO_PROVEDOR_EMBEDDINGS);
        assertThat(ollama.getLocal()).isTrue();
        assertThat(ollama.getDimensao()).isEqualTo(1024);
        assertThat(ollama.getModelos(0).getPrecoEntradaMilhaoUsd()).isEqualTo("0");
    }

    @Test
    void listarProvedoresSemTokenERecusado() {
        var semToken = AssistenteGrpc.newBlockingStub(canal);

        assertThatThrownBy(() -> semToken.listarProvedores(ListarProvedoresRequest.getDefaultInstance()))
                .satisfies(erro -> assertThat(((StatusRuntimeException) erro).getStatus().getCode())
                        .isEqualTo(Status.Code.UNAUTHENTICATED));
    }

    @Test
    void buscaQueCaiParaPalavraAvisaOUsuario() {
        when(busca.buscar(any(), anyString(), any(), anyInt())).thenReturn(new BuscaDocumentos.Resultado(
                List.of(), BuscaDocumentos.Modo.PALAVRA));
        claude.responde(ClaudeFalso.comTexto(
                "{\"nosDocumentos\":[],\"nosDadosGravados\":[],\"naoEncontrado\":true,"
                        + "\"sugestao\":\"não há extrato de setembro de 2026 enviado\"}", 50, 5));

        RespostaPergunta resposta = ultima(eventos(pedido("qual o saldo?")));

        assertThat(resposta.getSituacao()).isEqualTo(SituacaoResposta.SITUACAO_RESPOSTA_NAO_ENCONTRADA);
        assertThat(resposta.getAviso()).contains("busca por significado está indisponível");
        assertThat(resposta.getSugestao()).isEqualTo("não há extrato de setembro de 2026 enviado");
    }

    // -----------------------------------------------------------------------------------------------------------

    private List<PerguntarEvento> eventos(PerguntarRequest pedido) {
        List<PerguntarEvento> eventos = new ArrayList<>();
        Iterator<PerguntarEvento> fluxo = cliente.perguntar(pedido);
        while (fluxo.hasNext()) {
            eventos.add(fluxo.next());
        }
        return eventos;
    }

    private static List<EtapaPergunta> etapas(List<PerguntarEvento> eventos) {
        return eventos.stream().filter(PerguntarEvento::hasAndamento).map(e -> e.getAndamento().getEtapa()).toList();
    }

    private static RespostaPergunta ultima(List<PerguntarEvento> eventos) {
        assertThat(eventos.getLast().hasResposta()).isTrue();
        return eventos.getLast().getResposta();
    }

    private static PerguntarRequest pedido(String pergunta) {
        return PerguntarRequest.newBuilder()
                .setCondominioId(CONDOMINIO)
                .setPergunta(pergunta)
                .setConfiguracao(configuracao())
                .build();
    }

    private static ConfiguracaoPergunta configuracao() {
        return ConfiguracaoPergunta.newBuilder()
                .setProvedor("anthropic")
                .setModelo("claude-sonnet-5-5")
                .setModoBusca(ModoBusca.MODO_BUSCA_HIBRIDA)
                .setChaveCifrada(ByteString.copyFrom(cifrar("sk-ant-chave-do-condominio", par.getPublic())))
                .build();
    }

    private static CatalogoProvedores catalogo() {
        return new CatalogoProvedores(new PropriedadesIa(List.of(
                new PropriedadesIa.ProvedorIa("anthropic", "Anthropic (Claude)", "anthropic",
                        PropriedadesIa.Uso.RESPOSTAS, false, true, 0, List.of(
                                new PropriedadesIa.ModeloIa("claude-sonnet-5-5", "Claude Sonnet 5.5", true, "2.00",
                                        "10.00"),
                                new PropriedadesIa.ModeloIa("claude-haiku-4-5", "Claude Haiku 4.5", false, "1.00",
                                        "5.00"))),
                new PropriedadesIa.ProvedorIa("ollama-local", "Ollama local", "ollama",
                        PropriedadesIa.Uso.EMBEDDINGS, true, false, 1024, List.of(
                                new PropriedadesIa.ModeloIa("bge-m3", "BGE-M3", true, "0", "0"))))));
    }

    private static PropriedadesRag propriedades(String urlDoClaude) {
        var assistente = new PropriedadesRag.Assistente("", false, urlDoClaude, 20, 0, 16000, "medium", 6, 10,
                "localhost:9090", 5, List.of("desvio", "fraude", "roubo", "culpa"));
        return new PropriedadesRag(null, null, null, null, null, assistente);
    }

    /** Mesmo envelope do backend (assistente.proto); aqui só para o teste ter uma chave cifrada de verdade. */
    private static byte[] cifrar(String chave, PublicKey publica) {
        try {
            KeyGenerator gerador = KeyGenerator.getInstance("AES");
            gerador.init(256);
            SecretKey aes = gerador.generateKey();
            Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
            rsa.init(Cipher.ENCRYPT_MODE, publica, new javax.crypto.spec.OAEPParameterSpec("SHA-256", "MGF1",
                    java.security.spec.MGF1ParameterSpec.SHA256, javax.crypto.spec.PSource.PSpecified.DEFAULT));
            byte[] chaveCifrada = rsa.doFinal(aes.getEncoded());
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);
            Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
            gcm.init(Cipher.ENCRYPT_MODE, aes, new GCMParameterSpec(128, nonce));
            byte[] cifrado = gcm.doFinal(chave.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] envelope = new byte[3 + chaveCifrada.length + 12 + cifrado.length];
            envelope[0] = 1;
            envelope[1] = (byte) (chaveCifrada.length >> 8);
            envelope[2] = (byte) chaveCifrada.length;
            System.arraycopy(chaveCifrada, 0, envelope, 3, chaveCifrada.length);
            System.arraycopy(nonce, 0, envelope, 3 + chaveCifrada.length, 12);
            System.arraycopy(cifrado, 0, envelope, 3 + chaveCifrada.length + 12, cifrado.length);
            return envelope;
        } catch (Exception erro) {
            throw new IllegalStateException(erro);
        }
    }
}
