package br.com.condominioauditoria.backend.assistente;

import br.com.condominioauditoria.backend.assistente.DtosAssistente.CitacaoDocumento;
import br.com.condominioauditoria.backend.assistente.DtosAssistente.DadoGravado;
import br.com.condominioauditoria.backend.assistente.DtosAssistente.LinhaDado;
import br.com.condominioauditoria.backend.assistente.DtosAssistente.ParagrafoDocumentos;
import br.com.condominioauditoria.backend.assistente.DtosAssistente.ParametroConsulta;
import br.com.condominioauditoria.backend.assistente.DtosAssistente.PedidoPergunta;
import br.com.condominioauditoria.backend.assistente.DtosAssistente.RespostaAssistente;
import br.com.condominioauditoria.backend.assistente.DtosAssistente.SituacaoResposta;
import br.com.condominioauditoria.backend.assistente.DtosAssistente.TrocaConversa;
import br.com.condominioauditoria.backend.grpc.ClienteAssistente;
import br.com.condominioauditoria.backend.ia.ConfiguracaoIaServico;
import br.com.condominioauditoria.backend.ia.ConfiguracaoIaServico.Efetiva;
import br.com.condominioauditoria.backend.modulo.ModoIa;
import br.com.condominioauditoria.backend.modulo.Modulos;
import br.com.condominioauditoria.backend.modulo.PedidoInvalidoException;
import br.com.condominioauditoria.backend.modulo.RegistroUso;
import br.com.condominioauditoria.backend.seguranca.AcessoCondominio;
import br.com.condominioauditoria.contratos.assistente.v1.ConfiguracaoPergunta;
import br.com.condominioauditoria.contratos.assistente.v1.FiltrosBusca;
import br.com.condominioauditoria.contratos.assistente.v1.ModoBusca;
import br.com.condominioauditoria.contratos.assistente.v1.PerguntarRequest;
import br.com.condominioauditoria.contratos.assistente.v1.RespostaPergunta;
import br.com.condominioauditoria.contratos.assistente.v1.Trecho;
import br.com.condominioauditoria.contratos.assistente.v1.UsoPergunta;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Pergunta ao chat do Assistente (RF-04.8 a 04.16; ADR 0003, Decisão 5.2). Quem verifica o quê:
 * <ol>
 * <li>backend: acesso ao condomínio (o perfil já foi conferido na API), módulo ligado, modo de respostas efetivo
 * API_KEY com chave. MCP_EXTERNO, DESLIGADO, LOCAL ou sem chave = 409 sem chamar o rag;</li>
 * <li>rag: busca, modelo com ferramentas (que chamam a Consulta do backend com o mesmo token) e validação;</li>
 * <li>backend: segunda barreira nas citações, registro de uso e resposta em JSON.</li>
 * </ol>
 * Sem transação em volta: a thread da API espera o rag (até o prazo) sem segurar conexão de banco, e o rag chama a
 * Consulta do backend em outro grupo de threads.
 */
@Service
class PerguntaAssistente {

    private static final Logger log = LoggerFactory.getLogger(PerguntaAssistente.class);

    static final int PERGUNTA_MAXIMO = 2000;
    static final String TITULO_INDISPONIVEL = "Chat indisponível";
    static final String MSG_MCP_EXTERNO = "O assistente deste condomínio é o seu Claude, conectado ao MCP.";
    static final String MSG_DESLIGADO = "A IA está desligada neste condomínio.";
    static final String MSG_LOCAL = "O modo LOCAL de respostas ainda não está disponível neste condomínio.";
    static final String MSG_SEM_CHAVE = "A chave de IA deste condomínio não está cadastrada; o Admin precisa"
            + " cadastrar a chave na configuração de IA.";
    static final String MSG_CHAVE_ILEGIVEL = "A chave de IA deste condomínio não pôde ser usada; cadastre a chave de"
            + " novo.";
    static final String MSG_CHAVE_RECUSADA = "A chave de IA do condomínio foi recusada pelo provedor. O Admin precisa"
            + " conferir a chave ou cadastrar outra.";
    static final String MSG_LIMITE = "O limite de uso do provedor de IA foi atingido. Tente de novo mais tarde.";
    static final String MSG_INDISPONIVEL = "O assistente está indisponível no momento (serviço rag ou provedor de IA"
            + " fora do ar). Tente de novo em instantes.";

    private final AcessoCondominio acesso;
    private final Modulos modulos;
    private final ConfiguracaoIaServico configuracao;
    private final ClienteAssistente rag;
    private final BarreiraArquivos barreira;
    private final RegistroUso registroUso;
    private final int historicoTrocas;

    PerguntaAssistente(AcessoCondominio acesso, Modulos modulos, ConfiguracaoIaServico configuracao,
            ClienteAssistente rag, BarreiraArquivos barreira, RegistroUso registroUso,
            @Value("${condominio.assistente.historico-trocas:6}") int historicoTrocas) {
        this.acesso = acesso;
        this.modulos = modulos;
        this.configuracao = configuracao;
        this.rag = rag;
        this.barreira = barreira;
        this.registroUso = registroUso;
        this.historicoTrocas = Math.max(0, historicoTrocas);
    }

    /** Chamado pela API depois de conferir perfil, acesso e que o condomínio existe. */
    RespostaAssistente perguntar(UUID condominioId, PedidoPergunta pedido) {
        modulos.exigir(condominioId, Modulos.ASSISTENTE);
        Efetiva config = configuracao.ler(condominioId);
        exigirChatDisponivel(config);

        String pergunta = pedido == null || pedido.pergunta() == null ? "" : pedido.pergunta().strip();
        if (pergunta.isEmpty()) {
            throw new PedidoInvalidoException("Escreva a pergunta");
        }
        if (pergunta.length() > PERGUNTA_MAXIMO) {
            throw new PedidoInvalidoException("A pergunta passa de " + PERGUNTA_MAXIMO + " caracteres");
        }
        PerguntarRequest pedidoRag = montar(condominioId, pergunta, pedido, config);
        String autorizacao = acesso.tokenBearer().orElseThrow(() -> new IllegalStateException("Token ausente"));

        RespostaPergunta resposta;
        try {
            resposta = rag.perguntar(pedidoRag, autorizacao);
        } catch (StatusRuntimeException erro) {
            throw traduzir(erro, rag.prazoPerguntaSegundos());
        }

        RespostaAssistente saida = filtrar(condominioId, resposta, config);
        UsoPergunta uso = resposta.getUso();
        registroUso.pergunta(condominioId, acesso.usuario(),
                uso.getProvedor().isBlank() ? config.respostas().provedor() : uso.getProvedor(),
                uso.getModelo().isBlank() ? config.respostas().modelo() : uso.getModelo(), uso.getTokensEntrada(),
                uso.getTokensSaida(), uso.getVersaoPrompt());
        return saida;
    }

    /** Recusa (409, sem chamar o rag) quando o modo de respostas efetivo não é API_KEY com chave (RF-04.16). */
    static void exigirChatDisponivel(Efetiva config) {
        ModoIa modo = config.respostas().modoEfetivo();
        String mensagem = switch (modo) {
            case MCP_EXTERNO -> MSG_MCP_EXTERNO;
            case DESLIGADO -> MSG_DESLIGADO;
            case LOCAL -> MSG_LOCAL;
            case API_KEY -> config.respostas().chatDisponivel() ? null : MSG_SEM_CHAVE;
        };
        if (mensagem != null) {
            throw new RecusaAssistenteException(HttpStatus.CONFLICT, TITULO_INDISPONIVEL, mensagem, modo);
        }
    }

    PerguntarRequest montar(UUID condominioId, String pergunta, PedidoPergunta pedido, Efetiva config) {
        var r = config.respostas();
        var e = config.embeddings();
        var construtor = PerguntarRequest.newBuilder()
                .setCondominioId(condominioId.toString())
                .setPergunta(pergunta)
                .setConfiguracao(ConfiguracaoPergunta.newBuilder()
                        .setProvedor(r.provedor())
                        .setModelo(Objects.requireNonNullElse(r.modelo(), ""))
                        .setChaveCifrada(ByteString.copyFrom(r.chaveCifrada()))
                        .setModeloEmbeddings(e.modo() == ModoIa.LOCAL ? Objects.requireNonNullElse(e.modelo(), "") : "")
                        .setModoBusca(e.modo() == ModoIa.DESLIGADO ? ModoBusca.MODO_BUSCA_PALAVRA
                                : ModoBusca.MODO_BUSCA_HIBRIDA));
        List<TrocaConversa> historico = PedidosRag.lista(pedido.historico()).stream().filter(Objects::nonNull).toList();
        for (TrocaConversa t : historico.subList(Math.max(0, historico.size() - historicoTrocas), historico.size())) {
            construtor.addHistorico(br.com.condominioauditoria.contratos.assistente.v1.TrocaConversa.newBuilder()
                    .setPergunta(Objects.requireNonNullElse(t.pergunta(), ""))
                    .setResposta(Objects.requireNonNullElse(t.resposta(), "")));
        }
        FiltrosBusca filtros = PedidosRag.filtros(pedido.filtros());
        if (filtros != null) {
            construtor.setFiltros(filtros);
        }
        return construtor.build();
    }

    /**
     * Segunda barreira: descarta trechos de arquivos que não são do condomínio ou não existem mais, renumera as
     * citações (1, 2, ... na ordem dos trechos citados), tira parágrafo que ficou sem citação e, se nada sobrar,
     * responde NAO_ENCONTRADA.
     */
    RespostaAssistente filtrar(UUID condominioId, RespostaPergunta resposta, Efetiva config) {
        String modelo = !resposta.getUso().getModelo().isBlank() ? resposta.getUso().getModelo()
                : Objects.requireNonNullElse(config.respostas().modelo(), "");
        String sugestao = vazioParaNulo(resposta.getSugestao());
        String aviso = vazioParaNulo(resposta.getAviso());
        if (resposta.getSituacao() != br.com.condominioauditoria.contratos.assistente.v1.SituacaoResposta
                .SITUACAO_RESPOSTA_RESPONDIDA) {
            return naoEncontrada(sugestao, aviso, modelo);
        }

        Set<String> visiveis = barreira.visiveis(condominioId, resposta.getTrechosCitadosList());
        Map<String, Trecho> permitidos = new LinkedHashMap<>();
        for (Trecho t : resposta.getTrechosCitadosList()) {
            if (BarreiraArquivos.permitido(t, visiveis)) {
                permitidos.putIfAbsent(t.getTrechoId(), t);
            }
        }
        // Só os trechos que algum parágrafo cita, na ordem de trechos_citados
        Set<String> citadosPorParagrafo = new java.util.HashSet<>();
        resposta.getNosDocumentosList().forEach(p -> citadosPorParagrafo.addAll(p.getTrechoIdsList()));
        Map<String, Integer> numeros = new LinkedHashMap<>();
        List<CitacaoDocumento> citacoes = new ArrayList<>();
        for (Trecho t : permitidos.values()) {
            if (citadosPorParagrafo.contains(t.getTrechoId())) {
                int numero = numeros.size() + 1;
                numeros.put(t.getTrechoId(), numero);
                citacoes.add(CitacaoDocumento.de(numero, t));
            }
        }
        List<ParagrafoDocumentos> paragrafos = new ArrayList<>();
        int descartados = 0;
        for (var p : resposta.getNosDocumentosList()) {
            List<Integer> nums = p.getTrechoIdsList().stream().map(numeros::get).filter(Objects::nonNull).distinct()
                    .sorted().toList();
            if (nums.isEmpty()) {
                descartados++;
            } else {
                paragrafos.add(new ParagrafoDocumentos(p.getTexto(), nums));
            }
        }
        if (descartados > 0 || permitidos.size() < resposta.getTrechosCitadosCount()) {
            log.warn("Pergunta ao assistente: {} trecho(s) e {} parágrafo(s) descartados pela segunda barreira"
                    + " (condomínio {})", resposta.getTrechosCitadosCount() - permitidos.size(), descartados,
                    condominioId);
        }
        List<DadoGravado> dados = resposta.getNosDadosGravadosList().stream()
                .map(d -> new DadoGravado(d.getConsulta(),
                        d.getParametrosList().stream().map(x -> new ParametroConsulta(x.getNome(), x.getValor())).toList(),
                        d.getLinhasList().stream().map(x -> new LinhaDado(x.getRotulo(), x.getValor())).toList(),
                        vazioParaNulo(d.getComentario())))
                .toList();
        if (paragrafos.isEmpty() && dados.isEmpty()) {
            // A sugestão foi escrita para a resposta descartada; não vale mais
            return naoEncontrada(null, aviso, modelo);
        }
        return new RespostaAssistente(SituacaoResposta.RESPONDIDA, paragrafos, dados, citacoes, sugestao, aviso,
                modelo);
    }

    private static RespostaAssistente naoEncontrada(String sugestao, String aviso, String modelo) {
        return new RespostaAssistente(SituacaoResposta.NAO_ENCONTRADA, List.of(), List.of(), List.of(), sugestao, aviso,
                modelo);
    }

    /** Status gRPC do rag → HTTP do contrato. A descrição do rag já vem em português e sem a chave. */
    static RecusaAssistenteException traduzir(StatusRuntimeException erro, long prazoSegundos) {
        Status status = erro.getStatus();
        String descricao = status.getDescription();
        return switch (status.getCode()) {
            case INVALID_ARGUMENT -> new RecusaAssistenteException(HttpStatus.BAD_REQUEST, "Pergunta inválida",
                    Objects.requireNonNullElse(descricao, "Pergunta inválida"), null);
            case FAILED_PRECONDITION -> new RecusaAssistenteException(HttpStatus.CONFLICT, TITULO_INDISPONIVEL,
                    descricao == null || descricao.isBlank() ? MSG_CHAVE_ILEGIVEL : descricao, ModoIa.API_KEY);
            case PERMISSION_DENIED -> new RecusaAssistenteException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Chave de IA recusada", MSG_CHAVE_RECUSADA, null);
            case RESOURCE_EXHAUSTED -> new RecusaAssistenteException(HttpStatus.TOO_MANY_REQUESTS,
                    "Limite do provedor de IA", MSG_LIMITE, null);
            case DEADLINE_EXCEEDED -> new RecusaAssistenteException(HttpStatus.GATEWAY_TIMEOUT, "Prazo esgotado",
                    "A resposta passou do prazo de " + prazoSegundos + " segundos. Tente de novo ou faça uma pergunta"
                            + " mais específica.", null);
            default -> {
                log.warn("Pergunta ao assistente: rag respondeu {} ({})", status.getCode(), descricao);
                yield new RecusaAssistenteException(HttpStatus.SERVICE_UNAVAILABLE, "Assistente indisponível",
                        MSG_INDISPONIVEL, null);
            }
        };
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }
}
