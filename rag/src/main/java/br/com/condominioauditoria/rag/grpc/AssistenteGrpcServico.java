package br.com.condominioauditoria.rag.grpc;

import br.com.condominioauditoria.contratos.assistente.v1.Andamento;
import br.com.condominioauditoria.contratos.assistente.v1.AssistenteGrpc;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarRequest;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarResponse;
import br.com.condominioauditoria.contratos.assistente.v1.DadoGravado;
import br.com.condominioauditoria.contratos.assistente.v1.EtapaPergunta;
import br.com.condominioauditoria.contratos.assistente.v1.FiltrosBusca;
import br.com.condominioauditoria.contratos.assistente.v1.LinhaDado;
import br.com.condominioauditoria.contratos.assistente.v1.ListarProvedoresRequest;
import br.com.condominioauditoria.contratos.assistente.v1.ListarProvedoresResponse;
import br.com.condominioauditoria.contratos.assistente.v1.LocalPagina;
import br.com.condominioauditoria.contratos.assistente.v1.LocalParagrafos;
import br.com.condominioauditoria.contratos.assistente.v1.LocalPlanilha;
import br.com.condominioauditoria.contratos.assistente.v1.ModeloProvedor;
import br.com.condominioauditoria.contratos.assistente.v1.ModoBusca;
import br.com.condominioauditoria.contratos.assistente.v1.ParagrafoDocumentos;
import br.com.condominioauditoria.contratos.assistente.v1.ParametroConsulta;
import br.com.condominioauditoria.contratos.assistente.v1.PerguntarEvento;
import br.com.condominioauditoria.contratos.assistente.v1.PerguntarRequest;
import br.com.condominioauditoria.contratos.assistente.v1.Provedor;
import br.com.condominioauditoria.contratos.assistente.v1.RespostaPergunta;
import br.com.condominioauditoria.contratos.assistente.v1.SituacaoResposta;
import br.com.condominioauditoria.contratos.assistente.v1.Trecho;
import br.com.condominioauditoria.contratos.assistente.v1.UsoPergunta;
import br.com.condominioauditoria.contratos.assistente.v1.UsoProvedor;
import br.com.condominioauditoria.rag.assistente.catalogo.CatalogoProvedores;
import br.com.condominioauditoria.rag.assistente.catalogo.PropriedadesIa;
import br.com.condominioauditoria.rag.assistente.chave.ChavesRag;
import br.com.condominioauditoria.rag.assistente.chave.EnvelopeChave;
import br.com.condominioauditoria.rag.assistente.gateway.ContratoModelo;
import br.com.condominioauditoria.rag.assistente.pergunta.DadoConsultado;
import br.com.condominioauditoria.rag.assistente.pergunta.PedidoPergunta;
import br.com.condominioauditoria.rag.assistente.pergunta.ResultadoPergunta;
import br.com.condominioauditoria.rag.assistente.pergunta.ServicoPergunta;
import br.com.condominioauditoria.rag.indice.BuscaDocumentos;
import br.com.condominioauditoria.rag.indice.GeradorEmbeddings;
import br.com.condominioauditoria.rag.indice.Localizacao;
import br.com.condominioauditoria.rag.indice.RepositorioIndice;
import br.com.condominioauditoria.rag.indice.TrechoEncontrado;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementação de contracts/grpc/assistente/v1: Buscar (entrega 1), Perguntar e ListarProvedores (entrega 3).
 * Valida a entrada (INVALID_ARGUMENT com motivo legível), converte para a busca do índice e devolve os trechos
 * citáveis na ordem de relevância; no Perguntar, manda os eventos de andamento e, por último, a resposta já
 * conferida.
 *
 * Nenhuma mensagem de erro carrega a chave de IA nem pedaço dela.
 */
@Component
public class AssistenteGrpcServico extends AssistenteGrpc.AssistenteImplBase {

    private static final Logger log = LoggerFactory.getLogger(AssistenteGrpcServico.class);

    private final BuscaDocumentos busca;
    private final GeradorEmbeddings embeddings;
    private final ServicoPergunta perguntas;
    private final CatalogoProvedores catalogo;
    private final ChavesRag chaves;

    public AssistenteGrpcServico(BuscaDocumentos busca, GeradorEmbeddings embeddings, ServicoPergunta perguntas,
            CatalogoProvedores catalogo, ChavesRag chaves) {
        this.busca = busca;
        this.embeddings = embeddings;
        this.perguntas = perguntas;
        this.catalogo = catalogo;
        this.chaves = chaves;
    }

    @Override
    public void buscar(BuscarRequest pedido, StreamObserver<BuscarResponse> resposta) {
        RepositorioIndice.FiltrosBusca filtros;
        BuscaDocumentos.Modo modo;
        try {
            if (pedido.getTexto().isBlank()) {
                throw new IllegalArgumentException("Texto da busca é obrigatório");
            }
            if (pedido.getLimite() < 0) {
                throw new IllegalArgumentException("Limite não pode ser negativo");
            }
            if (!embeddings.aceita(pedido.getModeloEmbeddings())) {
                throw new IllegalArgumentException("Modelo de embeddings " + pedido.getModeloEmbeddings()
                        + " não está disponível neste rag (disponível: " + embeddings.modelo() + ")");
            }
            filtros = filtros(pedido);
            modo = pedido.getModo() == ModoBusca.MODO_BUSCA_PALAVRA ? BuscaDocumentos.Modo.PALAVRA
                    : BuscaDocumentos.Modo.HIBRIDA;
        } catch (IllegalArgumentException erro) {
            resposta.onError(Status.INVALID_ARGUMENT.withDescription(erro.getMessage()).asRuntimeException());
            return;
        }
        try {
            BuscaDocumentos.Resultado resultado = busca.buscar(filtros, pedido.getTexto(), modo, pedido.getLimite());
            var saida = BuscarResponse.newBuilder()
                    .setModoUsado(resultado.modoUsado() == BuscaDocumentos.Modo.PALAVRA ? ModoBusca.MODO_BUSCA_PALAVRA
                            : ModoBusca.MODO_BUSCA_HIBRIDA);
            resultado.trechos().forEach(t -> saida.addTrechos(trecho(t)));
            resposta.onNext(saida.build());
            resposta.onCompleted();
        } catch (RuntimeException erro) {
            log.warn("Falha na busca do condomínio {}: {}", pedido.getCondominioId(), erro.getMessage(), erro);
            resposta.onError(Status.INTERNAL.withDescription("Falha na busca nos documentos").asRuntimeException());
        }
    }

    // -----------------------------------------------------------------------------------------------------------
    // Perguntar (entrega 3)
    // -----------------------------------------------------------------------------------------------------------

    @Override
    public void perguntar(PerguntarRequest pedido, StreamObserver<PerguntarEvento> fluxo) {
        String autorizacao = AutorizacaoGrpc.AUTORIZACAO.get();
        if (autorizacao == null || autorizacao.isBlank()) {
            fluxo.onError(Status.UNAUTHENTICATED
                    .withDescription("Chamada sem token: o metadado authorization é obrigatório.")
                    .asRuntimeException());
            return;
        }
        PedidoPergunta entrada;
        try {
            entrada = traduzir(pedido, autorizacao);
        } catch (IllegalArgumentException erro) {
            fluxo.onError(Status.INVALID_ARGUMENT.withDescription(erro.getMessage()).asRuntimeException());
            return;
        }
        try {
            ResultadoPergunta resultado = perguntas.responder(entrada, (etapa, tentativa) -> fluxo
                    .onNext(PerguntarEvento.newBuilder().setAndamento(Andamento.newBuilder()
                            .setEtapa(etapa(etapa)).setTentativa(tentativa)).build()));
            fluxo.onNext(PerguntarEvento.newBuilder().setResposta(resposta(resultado)).build());
            fluxo.onCompleted();
        } catch (ServicoPergunta.PerguntaInvalidaException erro) {
            fluxo.onError(Status.INVALID_ARGUMENT.withDescription(erro.getMessage()).asRuntimeException());
        } catch (ServicoPergunta.ConfiguracaoFaltandoException | CatalogoProvedores.ModeloForaDoCatalogoException
                | ChavesRag.SemParDeChavesException erro) {
            fluxo.onError(Status.FAILED_PRECONDITION.withDescription(erro.getMessage()).asRuntimeException());
        } catch (EnvelopeChave.ChaveIlegivelException erro) {
            log.warn("Chave de IA do condomínio {} não pôde ser lida: {}", pedido.getCondominioId(),
                    erro.getMessage());
            fluxo.onError(Status.FAILED_PRECONDITION.withDescription(
                    "A chave de IA deste condomínio não pôde ser lida; cadastre de novo.").asRuntimeException());
        } catch (ContratoModelo.ErroProvedorException erro) {
            fluxo.onError(erroDoProvedor(erro));
        } catch (StatusRuntimeException erro) {
            // Vem das ferramentas numéricas no backend (gRPC Consulta)
            log.warn("Ferramenta do assistente falhou: {}", erro.getStatus());
            fluxo.onError(Status.UNAVAILABLE.withDescription(
                    "Não foi possível consultar os dados gravados agora; tente de novo em instantes.")
                    .asRuntimeException());
        } catch (RuntimeException erro) {
            log.error("Falha ao responder a pergunta do condomínio {}: {}", pedido.getCondominioId(),
                    erro.getMessage(), erro);
            fluxo.onError(Status.INTERNAL.withDescription("Falha ao responder a pergunta.").asRuntimeException());
        }
    }

    private static StatusRuntimeException erroDoProvedor(ContratoModelo.ErroProvedorException erro) {
        return switch (erro.tipo()) {
            case CHAVE_RECUSADA -> Status.PERMISSION_DENIED
                    .withDescription("A chave de API do condomínio foi recusada pelo provedor.").asRuntimeException();
            case LIMITE -> Status.RESOURCE_EXHAUSTED
                    .withDescription("O limite de uso da chave de IA deste condomínio foi atingido; tente mais tarde.")
                    .asRuntimeException();
            case INDISPONIVEL -> Status.UNAVAILABLE
                    .withDescription("O provedor de IA está indisponível agora; tente de novo em instantes.")
                    .asRuntimeException();
            case PEDIDO_INVALIDO -> Status.INTERNAL
                    .withDescription("O provedor de IA recusou o pedido do assistente.").asRuntimeException();
        };
    }

    private PedidoPergunta traduzir(PerguntarRequest pedido, String autorizacao) {
        String pergunta = pedido.getPergunta().strip();
        if (pergunta.isEmpty()) {
            throw new IllegalArgumentException("A pergunta é obrigatória.");
        }
        if (pergunta.length() > PedidoPergunta.MAXIMO_CARACTERES) {
            throw new IllegalArgumentException(
                    "A pergunta passa de " + PedidoPergunta.MAXIMO_CARACTERES + " caracteres.");
        }
        if (pedido.getLimiteTrechos() < 0) {
            throw new IllegalArgumentException("limite_trechos não pode ser negativo");
        }
        var configuracao = pedido.getConfiguracao();
        if (!embeddings.aceita(configuracao.getModeloEmbeddings())) {
            throw new IllegalArgumentException("Modelo de embeddings " + configuracao.getModeloEmbeddings()
                    + " não está disponível neste rag (disponível: " + embeddings.modelo() + ")");
        }
        var filtros = filtros(pedido.getCondominioId(), pedido.getFiltros());
        var modo = configuracao.getModoBusca() == ModoBusca.MODO_BUSCA_PALAVRA ? BuscaDocumentos.Modo.PALAVRA
                : BuscaDocumentos.Modo.HIBRIDA;
        var historico = pedido.getHistoricoList().stream()
                .map(t -> new PedidoPergunta.Troca(t.getPergunta(), t.getResposta())).toList();
        return new PedidoPergunta(filtros, pergunta, historico, modo, configuracao.getProvedor(),
                configuracao.getModelo(), configuracao.getChaveCifrada().toByteArray(), pedido.getLimiteTrechos(),
                autorizacao);
    }

    private static EtapaPergunta etapa(ServicoPergunta.Etapa etapa) {
        return switch (etapa) {
            case BUSCANDO_TRECHOS -> EtapaPergunta.ETAPA_PERGUNTA_BUSCANDO_TRECHOS;
            case CONSULTANDO_DADOS -> EtapaPergunta.ETAPA_PERGUNTA_CONSULTANDO_DADOS;
            case REDIGINDO -> EtapaPergunta.ETAPA_PERGUNTA_REDIGINDO;
            case VALIDANDO -> EtapaPergunta.ETAPA_PERGUNTA_VALIDANDO;
            case NOVA_TENTATIVA -> EtapaPergunta.ETAPA_PERGUNTA_NOVA_TENTATIVA;
        };
    }

    private static RespostaPergunta resposta(ResultadoPergunta resultado) {
        var saida = RespostaPergunta.newBuilder()
                .setSituacao(resultado.situacao() == ResultadoPergunta.Situacao.RESPONDIDA
                        ? SituacaoResposta.SITUACAO_RESPOSTA_RESPONDIDA
                        : SituacaoResposta.SITUACAO_RESPOSTA_NAO_ENCONTRADA)
                .setSugestao(resultado.sugestao() == null ? "" : resultado.sugestao())
                .setAviso(resultado.aviso() == null ? "" : resultado.aviso())
                .setUso(uso(resultado.uso()));
        resultado.nosDocumentos().forEach(p -> saida.addNosDocumentos(ParagrafoDocumentos.newBuilder()
                .setTexto(p.texto()).addAllTrechoIds(p.trechoIds())));
        resultado.nosDadosGravados().forEach(d -> saida.addNosDadosGravados(dadoGravado(d)));
        resultado.trechosCitados().forEach(t -> saida.addTrechosCitados(trecho(t)));
        return saida.build();
    }

    private static DadoGravado dadoGravado(ResultadoPergunta.Dado dado) {
        DadoConsultado consultado = dado.consultado();
        var saida = DadoGravado.newBuilder()
                .setChamadaId(consultado.chamadaId())
                .setConsulta(consultado.consulta())
                .setComentario(dado.comentario() == null ? "" : dado.comentario());
        consultado.parametros().forEach(p -> saida.addParametros(ParametroConsulta.newBuilder()
                .setNome(p.nome()).setValor(p.valor())));
        consultado.linhas().forEach(l -> saida.addLinhas(LinhaDado.newBuilder()
                .setRotulo(l.rotulo()).setValor(l.valor())));
        return saida.build();
    }

    private static UsoPergunta uso(ResultadoPergunta.Uso uso) {
        return UsoPergunta.newBuilder()
                .setTokensEntrada(uso.tokensEntrada())
                .setTokensSaida(uso.tokensSaida())
                .setProvedor(uso.provedor())
                .setModelo(uso.modelo())
                .setVersaoPrompt(uso.versaoPrompt())
                .setTentativas(uso.tentativas())
                .build();
    }

    // -----------------------------------------------------------------------------------------------------------
    // ListarProvedores (entrega 3)
    // -----------------------------------------------------------------------------------------------------------

    @Override
    public void listarProvedores(ListarProvedoresRequest pedido, StreamObserver<ListarProvedoresResponse> resposta) {
        String autorizacao = AutorizacaoGrpc.AUTORIZACAO.get();
        if (autorizacao == null || autorizacao.isBlank()) {
            resposta.onError(Status.UNAUTHENTICATED
                    .withDescription("Chamada sem token: o metadado authorization é obrigatório.")
                    .asRuntimeException());
            return;
        }
        var saida = ListarProvedoresResponse.newBuilder().setChavePublicaPem(chaves.publicaPem());
        catalogo.provedores().forEach(p -> saida.addProvedores(provedor(p)));
        resposta.onNext(saida.build());
        resposta.onCompleted();
    }

    private static Provedor provedor(PropriedadesIa.ProvedorIa provedor) {
        var saida = Provedor.newBuilder()
                .setCodigo(provedor.codigo())
                .setNome(provedor.nome())
                .setTipo(provedor.tipo())
                .setUso(provedor.uso() == PropriedadesIa.Uso.RESPOSTAS ? UsoProvedor.USO_PROVEDOR_RESPOSTAS
                        : UsoProvedor.USO_PROVEDOR_EMBEDDINGS)
                .setLocal(provedor.local())
                .setPrecisaChave(provedor.precisaChave())
                .setDimensao(provedor.dimensao());
        provedor.modelos().forEach(m -> saida.addModelos(ModeloProvedor.newBuilder()
                .setId(m.id())
                .setNome(m.nome())
                .setPadrao(m.padrao())
                .setPrecoEntradaMilhaoUsd(m.precoEntradaMilhaoUsd())
                .setPrecoSaidaMilhaoUsd(m.precoSaidaMilhaoUsd())));
        return saida.build();
    }

    private static RepositorioIndice.FiltrosBusca filtros(BuscarRequest pedido) {
        return filtros(pedido.getCondominioId(), pedido.getFiltros());
    }

    private static RepositorioIndice.FiltrosBusca filtros(String condominioId, FiltrosBusca f) {
        UUID condominio = uuid(condominioId, "condominio_id");
        LocalDate inicio = data(f.getDataInicio(), "data_inicio");
        LocalDate fim = data(f.getDataFim(), "data_fim");
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            throw new IllegalArgumentException("data_inicio depois de data_fim");
        }
        List<UUID> arquivos = f.getArquivoIdsList().stream().map(id -> uuid(id, "arquivo_ids")).toList();
        List<String> categorias = f.getCategoriasList().stream().filter(c -> !c.isBlank()).toList();
        return new RepositorioIndice.FiltrosBusca(condominio, categorias, inicio, fim, arquivos);
    }

    private static UUID uuid(String valor, String campo) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException erro) {
            throw new IllegalArgumentException(campo + " inválido: \"" + valor + "\"");
        }
    }

    private static LocalDate data(String valor, String campo) {
        if (valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException erro) {
            throw new IllegalArgumentException(campo + " fora do formato AAAA-MM-DD: \"" + valor + "\"");
        }
    }

    public static Trecho trecho(TrechoEncontrado t) {
        var local = br.com.condominioauditoria.contratos.assistente.v1.Localizacao.newBuilder();
        switch (t.localizacao()) {
            case Localizacao.Pagina l -> local.setPagina(LocalPagina.newBuilder().setPagina(l.numero()));
            case Localizacao.Planilha l -> local.setPlanilha(LocalPlanilha.newBuilder().setAba(l.aba())
                    .setLinhaInicio(l.linhaInicio()).setLinhaFim(l.linhaFim()));
            case Localizacao.Paragrafos l -> local.setParagrafos(LocalParagrafos.newBuilder()
                    .setParagrafoInicio(l.inicio()).setParagrafoFim(l.fim())
                    .setSecao(l.secao() == null ? "" : l.secao()));
        }
        return Trecho.newBuilder()
                .setTrechoId(t.trechoId().toString())
                .setArquivoId(t.arquivoId().toString())
                .setNomeArquivo(t.nomeArquivo())
                .setCategoria(t.categoria())
                .setLocalizacao(local)
                .setTexto(t.texto())
                .setPontuacao(t.pontuacao())
                .setSha256(t.sha256())
                .build();
    }
}
