package br.com.condominioauditoria.backend.assistente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import br.com.condominioauditoria.backend.arquivo.ArquivoRepository;
import br.com.condominioauditoria.backend.arquivo.Categoria;
import br.com.condominioauditoria.backend.assistente.DtosAssistente.FiltrosDocumentos;
import br.com.condominioauditoria.backend.assistente.DtosAssistente.PedidoPergunta;
import br.com.condominioauditoria.backend.assistente.DtosAssistente.SituacaoResposta;
import br.com.condominioauditoria.backend.assistente.DtosAssistente.TrocaConversa;
import br.com.condominioauditoria.backend.ia.ConfiguracaoIaServico;
import br.com.condominioauditoria.backend.ia.ConfiguracaoIaServico.Efetiva;
import br.com.condominioauditoria.backend.ia.ConfiguracaoIaServico.Embeddings;
import br.com.condominioauditoria.backend.ia.ConfiguracaoIaServico.Respostas;
import br.com.condominioauditoria.backend.modulo.ModoIa;
import br.com.condominioauditoria.backend.modulo.ModuloNaoContratadoException;
import br.com.condominioauditoria.backend.modulo.Modulos;
import br.com.condominioauditoria.backend.modulo.PedidoInvalidoException;
import br.com.condominioauditoria.backend.modulo.RegistroUso;
import br.com.condominioauditoria.backend.seguranca.AcessoCondominio;
import br.com.condominioauditoria.contratos.assistente.v1.Andamento;
import br.com.condominioauditoria.contratos.assistente.v1.DadoGravado;
import br.com.condominioauditoria.contratos.assistente.v1.EtapaPergunta;
import br.com.condominioauditoria.contratos.assistente.v1.LinhaDado;
import br.com.condominioauditoria.contratos.assistente.v1.LocalPagina;
import br.com.condominioauditoria.contratos.assistente.v1.Localizacao;
import br.com.condominioauditoria.contratos.assistente.v1.ModoBusca;
import br.com.condominioauditoria.contratos.assistente.v1.ParagrafoDocumentos;
import br.com.condominioauditoria.contratos.assistente.v1.PerguntarEvento;
import br.com.condominioauditoria.contratos.assistente.v1.RespostaPergunta;
import br.com.condominioauditoria.contratos.assistente.v1.Trecho;
import br.com.condominioauditoria.contratos.assistente.v1.UsoPergunta;
import io.grpc.Status;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Pergunta ao chat (RF-04.8 a 04.16) com um rag falso em processo: recusas pelo modo sem chamar o rag, configuração
 * resolvida no pedido, histórico limitado, segunda barreira com renumeração, registro de uso e tradução dos erros.
 */
class PerguntaAssistenteTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final byte[] CHAVE_CIFRADA = {1, 2, 3, 4};

    private final ArquivoRepository arquivos = mock(ArquivoRepository.class);
    private final Modulos modulos = mock(Modulos.class);
    private final ConfiguracaoIaServico configuracao = mock(ConfiguracaoIaServico.class);
    private final RegistroUso registroUso = mock(RegistroUso.class);
    private final Arquivo ataA = new Arquivo(A, Categoria.ATA, "ata.pdf", "a/ata.pdf", "a".repeat(64), 1,
            "application/pdf", "gestor");
    private final Arquivo contratoA = new Arquivo(A, Categoria.CONTRATO, "contrato.pdf", "a/contrato.pdf",
            "c".repeat(64), 1, "application/pdf", "gestor");
    private final Arquivo ataB = new Arquivo(B, Categoria.ATA, "ata-b.pdf", "b/ata.pdf", "b".repeat(64), 1,
            "application/pdf", "gestor");
    private RagFalso rag;
    private PerguntaAssistente pergunta;

    @BeforeEach
    void preparar() throws Exception {
        rag = new RagFalso();
        when(arquivos.findByCondominioIdAndIdIn(eq(A), anyCollection())).thenAnswer(i -> {
            var ids = i.<java.util.Collection<UUID>>getArgument(1);
            return List.of(ataA, contratoA, ataB).stream()
                    .filter(a -> a.getCondominioId().equals(A) && ids.contains(a.getId())).toList();
        });
        configurar(ModoIa.API_KEY, CHAVE_CIFRADA, ModoIa.LOCAL);
        pergunta = new PerguntaAssistente(new AcessoCondominio(), modulos, configuracao, rag.cliente,
                new BarreiraArquivos(arquivos), registroUso, 6);
        logar("USUARIO");
    }

    @AfterEach
    void limpar() {
        rag.close();
        SecurityContextHolder.clearContext();
    }

    @Test
    void mcpExternoEh409ComAMensagemDoRf0416SemChamarORag() {
        configurar(ModoIa.MCP_EXTERNO, null, ModoIa.LOCAL);

        assertThatThrownBy(() -> pergunta.perguntar(A, pedido("qual o índice de reajuste?")))
                .isInstanceOfSatisfying(RecusaAssistenteException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("O assistente deste condomínio é o seu Claude, conectado ao MCP.");
                    assertThat(e.modoIa()).isEqualTo(ModoIa.MCP_EXTERNO);
                });
        assertThat(rag.perguntas).isEmpty();
        verifyNoInteractions(registroUso);
    }

    @Test
    void desligadoESemChaveSao409SemChamarORag() {
        configurar(ModoIa.DESLIGADO, null, ModoIa.DESLIGADO);
        assertThatThrownBy(() -> pergunta.perguntar(A, pedido("x")))
                .isInstanceOfSatisfying(RecusaAssistenteException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo("A IA está desligada neste condomínio.");
                    assertThat(e.modoIa()).isEqualTo(ModoIa.DESLIGADO);
                });

        configurar(ModoIa.API_KEY, null, ModoIa.LOCAL);
        assertThatThrownBy(() -> pergunta.perguntar(A, pedido("x")))
                .isInstanceOfSatisfying(RecusaAssistenteException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("não está cadastrada");
                    assertThat(e.modoIa()).isEqualTo(ModoIa.API_KEY);
                });
        assertThat(rag.perguntas).isEmpty();
    }

    @Test
    void moduloDesligadoRecusaAntesDeTudo() {
        doThrow(new ModuloNaoContratadoException(Modulos.ASSISTENTE, "Assistente")).when(modulos)
                .exigir(A, Modulos.ASSISTENTE);

        assertThatThrownBy(() -> pergunta.perguntar(A, pedido("x")))
                .isInstanceOf(ModuloNaoContratadoException.class)
                .hasMessage("Módulo Assistente não contratado para este condomínio.");
        assertThat(rag.perguntas).isEmpty();
        verifyNoInteractions(configuracao, registroUso);
    }

    @Test
    void perguntaVaziaOuLongaDemaisEh400() {
        assertThatThrownBy(() -> pergunta.perguntar(A, pedido("   "))).isInstanceOf(PedidoInvalidoException.class);
        assertThatThrownBy(() -> pergunta.perguntar(A, pedido("x".repeat(2001))))
                .isInstanceOf(PedidoInvalidoException.class);
        assertThatThrownBy(() -> pergunta.perguntar(A, new PedidoPergunta("x", null, new FiltrosDocumentos(null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 1, 1), null)))).isInstanceOf(PedidoInvalidoException.class);
        assertThat(rag.perguntas).isEmpty();
    }

    @Test
    void mandaAConfiguracaoResolvidaOTokenOsFiltrosEAsUltimasSeisTrocas() {
        responder(RespostaPergunta.newBuilder().setSituacao(
                br.com.condominioauditoria.contratos.assistente.v1.SituacaoResposta.SITUACAO_RESPOSTA_NAO_ENCONTRADA)
                .setUso(uso(10, 2)).build());
        List<TrocaConversa> historico = IntStream.rangeClosed(1, 8)
                .mapToObj(i -> new TrocaConversa("p" + i, "r" + i)).toList();

        pergunta.perguntar(A, new PedidoPergunta("  e o de portaria?  ", historico, new FiltrosDocumentos(
                List.of(Categoria.CONTRATO), LocalDate.of(2025, 1, 1), null, List.of(contratoA.getId()))));

        assertThat(rag.token.get()).isEqualTo("Bearer token-usuario");
        var pedido = rag.perguntas.getFirst();
        assertThat(pedido.getCondominioId()).isEqualTo(A.toString());
        assertThat(pedido.getPergunta()).isEqualTo("e o de portaria?");
        assertThat(pedido.getHistoricoList()).extracting(t -> t.getPergunta())
                .containsExactly("p3", "p4", "p5", "p6", "p7", "p8");
        assertThat(pedido.getConfiguracao().getProvedor()).isEqualTo("anthropic");
        assertThat(pedido.getConfiguracao().getModelo()).isEqualTo("claude-sonnet-5-5");
        assertThat(pedido.getConfiguracao().getChaveCifrada().toByteArray()).isEqualTo(CHAVE_CIFRADA);
        assertThat(pedido.getConfiguracao().getModeloEmbeddings()).isEqualTo("bge-m3");
        assertThat(pedido.getConfiguracao().getModoBusca()).isEqualTo(ModoBusca.MODO_BUSCA_HIBRIDA);
        assertThat(pedido.getFiltros().getCategoriasList()).containsExactly("CONTRATO");
        assertThat(pedido.getFiltros().getDataInicio()).isEqualTo("2025-01-01");
        assertThat(pedido.getFiltros().getDataFim()).isEmpty();
        assertThat(pedido.getFiltros().getArquivoIdsList()).containsExactly(contratoA.getId().toString());
    }

    @Test
    void embeddingsDesligadosPedemBuscaPorPalavra() {
        configurar(ModoIa.API_KEY, CHAVE_CIFRADA, ModoIa.DESLIGADO);
        responder(RespostaPergunta.newBuilder().setSituacao(
                br.com.condominioauditoria.contratos.assistente.v1.SituacaoResposta.SITUACAO_RESPOSTA_NAO_ENCONTRADA)
                .build());

        pergunta.perguntar(A, pedido("x"));

        assertThat(rag.perguntas.getFirst().getConfiguracao().getModoBusca()).isEqualTo(ModoBusca.MODO_BUSCA_PALAVRA);
        assertThat(rag.perguntas.getFirst().getConfiguracao().getModeloEmbeddings()).isEmpty();
    }

    @Test
    void segundaBarreiraDescartaArquivoDeOutroCondominioRenumeraETiraParagrafoSemCitacao() {
        Trecho daAtaB = trecho("t-b", ataB, 2);
        Trecho daAta = trecho("t-ata", ataA, 3);
        Trecho doContrato = trecho("t-contrato", contratoA, 4);
        Trecho inexistente = Trecho.newBuilder().setTrechoId("t-x").setArquivoId(UUID.randomUUID().toString()).build();
        responder(RespostaPergunta.newBuilder()
                .setSituacao(br.com.condominioauditoria.contratos.assistente.v1.SituacaoResposta.SITUACAO_RESPOSTA_RESPONDIDA)
                .addNosDocumentos(ParagrafoDocumentos.newBuilder().setTexto("Só do B.").addTrechoIds("t-b"))
                .addNosDocumentos(ParagrafoDocumentos.newBuilder().setTexto("A ata aprovou.").addTrechoIds("t-b")
                        .addTrechoIds("t-ata"))
                .addNosDocumentos(ParagrafoDocumentos.newBuilder().setTexto("O contrato prevê IPCA.")
                        .addTrechoIds("t-contrato").addTrechoIds("t-x"))
                .addNosDadosGravados(DadoGravado.newBuilder().setChamadaId("c1").setConsulta("resumo_fundos")
                        .addLinhas(LinhaDado.newBuilder().setRotulo("Saldo").setValor("R$ 1.234,56")))
                .addTrechosCitados(daAtaB).addTrechosCitados(daAta).addTrechosCitados(doContrato)
                .addTrechosCitados(inexistente)
                .setSugestao("").setAviso("Busca só por palavra.")
                .setUso(uso(1200, 340))
                .build());

        var resposta = pergunta.perguntar(A, pedido("a troca do portão foi aprovada e quanto foi pago?"));

        assertThat(resposta.situacao()).isEqualTo(SituacaoResposta.RESPONDIDA);
        assertThat(resposta.nosDocumentos()).extracting(p -> p.texto())
                .containsExactly("A ata aprovou.", "O contrato prevê IPCA.");
        assertThat(resposta.nosDocumentos()).extracting(p -> p.citacoes())
                .containsExactly(List.of(1), List.of(2));
        assertThat(resposta.citacoes()).extracting(c -> c.numero(), c -> c.arquivoId())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1, ataA.getId()),
                        org.assertj.core.groups.Tuple.tuple(2, contratoA.getId()));
        assertThat(resposta.citacoes().getFirst().localizacao().descricao()).isEqualTo("página 3");
        assertThat(resposta.citacoes().getFirst().sha256()).isEqualTo(ataA.getSha256());
        assertThat(resposta.nosDadosGravados()).singleElement().satisfies(d -> {
            assertThat(d.consulta()).isEqualTo("resumo_fundos");
            assertThat(d.linhas().getFirst().valor()).isEqualTo("R$ 1.234,56");
            assertThat(d.comentario()).isNull();
        });
        assertThat(resposta.sugestao()).isNull();
        assertThat(resposta.aviso()).isEqualTo("Busca só por palavra.");
        assertThat(resposta.modelo()).isEqualTo("claude-sonnet-5-5");
        verify(registroUso).pergunta(A, "pessoa.usuario", "anthropic", "claude-sonnet-5-5", 1200, 340, "2026-10-05.1");
    }

    @Test
    void semNadaQueSobreViraNaoEncontradaERegistraOUsoMesmoAssim() {
        responder(RespostaPergunta.newBuilder()
                .setSituacao(br.com.condominioauditoria.contratos.assistente.v1.SituacaoResposta.SITUACAO_RESPOSTA_RESPONDIDA)
                .addNosDocumentos(ParagrafoDocumentos.newBuilder().setTexto("Do B.").addTrechoIds("t-b"))
                .addTrechosCitados(trecho("t-b", ataB, 1))
                .setSugestao("não há ata de 2025 enviada")
                .setUso(uso(900, 100))
                .build());

        var resposta = pergunta.perguntar(A, pedido("o portão foi aprovado?"));

        assertThat(resposta.situacao()).isEqualTo(SituacaoResposta.NAO_ENCONTRADA);
        assertThat(resposta.nosDocumentos()).isEmpty();
        assertThat(resposta.citacoes()).isEmpty();
        assertThat(resposta.nosDadosGravados()).isEmpty();
        assertThat(resposta.sugestao()).isNull();
        verify(registroUso).pergunta(A, "pessoa.usuario", "anthropic", "claude-sonnet-5-5", 900, 100, "2026-10-05.1");
    }

    @Test
    void naoEncontradaDoRagPassaComASugestao() {
        responder(RespostaPergunta.newBuilder()
                .setSituacao(br.com.condominioauditoria.contratos.assistente.v1.SituacaoResposta.SITUACAO_RESPOSTA_NAO_ENCONTRADA)
                .setSugestao("não há contrato de jardinagem enviado").setUso(uso(500, 50)).build());

        var resposta = pergunta.perguntar(A, pedido("qual a empresa de jardinagem?"));

        assertThat(resposta.situacao()).isEqualTo(SituacaoResposta.NAO_ENCONTRADA);
        assertThat(resposta.sugestao()).isEqualTo("não há contrato de jardinagem enviado");
        verify(registroUso).pergunta(any(), any(), any(), any(), eq(500L), eq(50L), any());
    }

    @Test
    void errosDoRagViramOsStatusDoContratoSemRegistrarUso() {
        Map<Status, HttpStatus> casos = Map.of(
                Status.FAILED_PRECONDITION.withDescription("A chave de IA deste condomínio não pôde ser lida; cadastre"
                        + " de novo"), HttpStatus.CONFLICT,
                Status.PERMISSION_DENIED.withDescription("chave recusada"), HttpStatus.UNPROCESSABLE_CONTENT,
                Status.RESOURCE_EXHAUSTED, HttpStatus.TOO_MANY_REQUESTS,
                Status.UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                Status.DEADLINE_EXCEEDED, HttpStatus.GATEWAY_TIMEOUT,
                Status.UNIMPLEMENTED, HttpStatus.SERVICE_UNAVAILABLE,
                Status.INVALID_ARGUMENT.withDescription("pergunta acima de 2000 caracteres"), HttpStatus.BAD_REQUEST);
        List<String> erros = new ArrayList<>();
        casos.forEach((status, http) -> {
            rag.aoPerguntar = (p, r) -> r.onError(status.asRuntimeException());
            assertThatThrownBy(() -> pergunta.perguntar(A, pedido("x")))
                    .isInstanceOfSatisfying(RecusaAssistenteException.class, e -> {
                        assertThat(e.status()).as(status.getCode().name()).isEqualTo(http);
                        erros.add(e.getMessage());
                    });
        });
        assertThat(erros).anyMatch(m -> m.contains("cadastre"));
        assertThat(erros).anyMatch(m -> m.contains("120") || m.contains("7 segundos"));
        verifyNoInteractions(registroUso);
    }

    @Test
    void ragForaDoArEh503() {
        rag.desligar();

        assertThatThrownBy(() -> pergunta.perguntar(A, pedido("x")))
                .isInstanceOfSatisfying(RecusaAssistenteException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(registroUso);
    }

    // ---- apoio ----

    private void configurar(ModoIa modoRespostas, byte[] chave, ModoIa modoEmbeddings) {
        var respostas = new Respostas(modoRespostas, modoRespostas, chave == null ? null : "anthropic",
                chave == null ? null : "claude-sonnet-5-5", chave, chave == null ? null : "x9Qa");
        var embeddings = modoEmbeddings == ModoIa.LOCAL ? new Embeddings(ModoIa.LOCAL, "ollama-local", "bge-m3")
                : new Embeddings(modoEmbeddings, null, null);
        when(configuracao.ler(A)).thenReturn(new Efetiva(ModoIa.MCP_EXTERNO, respostas, embeddings, null, null));
    }

    private void responder(RespostaPergunta resposta) {
        rag.aoPerguntar = (p, r) -> {
            r.onNext(PerguntarEvento.newBuilder().setAndamento(Andamento.newBuilder()
                    .setEtapa(EtapaPergunta.ETAPA_PERGUNTA_BUSCANDO_TRECHOS).setTentativa(1)).build());
            r.onNext(PerguntarEvento.newBuilder().setResposta(resposta).build());
            r.onCompleted();
        };
    }

    private static UsoPergunta uso(long entrada, long saida) {
        return UsoPergunta.newBuilder().setTokensEntrada(entrada).setTokensSaida(saida).setProvedor("anthropic")
                .setModelo("claude-sonnet-5-5").setVersaoPrompt("2026-10-05.1").setTentativas(1).build();
    }

    private static Trecho trecho(String id, Arquivo arquivo, int pagina) {
        return Trecho.newBuilder().setTrechoId(id).setArquivoId(arquivo.getId().toString())
                .setNomeArquivo(arquivo.getNomeOriginal()).setCategoria(arquivo.getCategoria().name())
                .setLocalizacao(Localizacao.newBuilder().setPagina(LocalPagina.newBuilder().setPagina(pagina)))
                .setTexto("texto de " + arquivo.getNomeOriginal()).setSha256(arquivo.getSha256()).build();
    }

    private static PedidoPergunta pedido(String texto) {
        return new PedidoPergunta(texto, null, null);
    }

    static void logar(String perfil) {
        logar(perfil, A);
    }

    static void logar(String perfil, UUID condominio) {
        Jwt jwt = new Jwt("token-" + perfil.toLowerCase(), Instant.now(), Instant.now().plusSeconds(300),
                Map.of("alg", "none"), Map.of("preferred_username", "pessoa." + perfil.toLowerCase(), "condominios",
                        List.of(condominio.toString())));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + perfil)), "pessoa." + perfil.toLowerCase()));
    }
}
