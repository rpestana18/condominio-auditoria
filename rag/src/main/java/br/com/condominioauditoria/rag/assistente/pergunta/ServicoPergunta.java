package br.com.condominioauditoria.rag.assistente.pergunta;

import br.com.condominioauditoria.rag.assistente.catalogo.CatalogoProvedores;
import br.com.condominioauditoria.rag.assistente.catalogo.PropriedadesIa.ModeloIa;
import br.com.condominioauditoria.rag.assistente.catalogo.PropriedadesIa.ProvedorIa;
import br.com.condominioauditoria.rag.assistente.chave.ChavesRag;
import br.com.condominioauditoria.rag.assistente.gateway.ContratoModelo.ChamadaFerramenta;
import br.com.condominioauditoria.rag.assistente.gateway.ContratoModelo.Parada;
import br.com.condominioauditoria.rag.assistente.gateway.ContratoModelo.Parametros;
import br.com.condominioauditoria.rag.assistente.gateway.ContratoModelo.ResultadoFerramenta;
import br.com.condominioauditoria.rag.assistente.gateway.ContratoModelo.Turno;
import br.com.condominioauditoria.rag.assistente.gateway.GatewayIa;
import br.com.condominioauditoria.rag.assistente.pergunta.EsquemaResposta.ComentarioDado;
import br.com.condominioauditoria.rag.assistente.pergunta.EsquemaResposta.ParagrafoModelo;
import br.com.condominioauditoria.rag.assistente.pergunta.EsquemaResposta.RespostaModelo;
import br.com.condominioauditoria.rag.config.PropriedadesRag;
import br.com.condominioauditoria.rag.indice.BuscaDocumentos;
import br.com.condominioauditoria.rag.indice.TrechoEncontrado;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * O chat do assistente de ponta a ponta (RF-04.8 a 04.16, ADR 0003, Decisões 1 e 5.2):
 * <ol>
 * <li>busca híbrida (ou por palavra) filtrada nos documentos indexados do condomínio;</li>
 * <li>uma conversa com o modelo pelo ai-gateway, com os trechos e as quatro ferramentas numéricas; o laço de
 * ferramentas é feito aqui, com teto de voltas, e cada ferramenta roda no backend com o token do usuário;</li>
 * <li>conferência da resposta ({@link ValidadorResposta}); reprovou, uma nova tentativa com o motivo; reprovou de
 * novo, "Não encontrei nos documentos.";</li>
 * <li>o bloco "Nos dados gravados" é montado a partir do resultado das ferramentas, nunca do texto do modelo.</li>
 * </ol>
 * Nada de configuração de condomínio é lido de banco; nada da chave de API vai para log.
 */
@Service
public class ServicoPergunta {

    private static final Logger log = LoggerFactory.getLogger(ServicoPergunta.class);

    /** Uma nova tentativa, como a especificação manda (RF-04.12). */
    private static final int TENTATIVAS = 2;

    private final BuscaDocumentos busca;
    private final CatalogoProvedores catalogo;
    private final ChavesRag chaves;
    private final GatewayIa gateway;
    private final FerramentasNumericas ferramentas;
    private final ClienteConsulta clienteConsulta;
    private final ValidadorResposta validador;
    private final PropriedadesRag.Assistente config;

    public ServicoPergunta(BuscaDocumentos busca, CatalogoProvedores catalogo, ChavesRag chaves, GatewayIa gateway,
            FerramentasNumericas ferramentas, ClienteConsulta clienteConsulta, ValidadorResposta validador,
            PropriedadesRag propriedades) {
        this.busca = busca;
        this.catalogo = catalogo;
        this.chaves = chaves;
        this.gateway = gateway;
        this.ferramentas = ferramentas;
        this.clienteConsulta = clienteConsulta;
        this.validador = validador;
        this.config = propriedades.assistente();
    }

    /** Etapas do evento de andamento do fluxo (assistente.proto, EtapaPergunta). */
    public enum Etapa {
        BUSCANDO_TRECHOS, CONSULTANDO_DADOS, REDIGINDO, VALIDANDO, NOVA_TENTATIVA
    }

    /** Quem recebe os eventos de andamento (o fluxo gRPC). */
    public interface Andamentos {
        void etapa(Etapa etapa, int tentativa);
    }

    /** Pergunta vazia, longa demais ou filtro fora do formato: INVALID_ARGUMENT. */
    public static class PerguntaInvalidaException extends RuntimeException {
        public PerguntaInvalidaException(String mensagem) {
            super(mensagem);
        }
    }

    /** Falta configuração para responder (chave ausente, provedor fora do catálogo): FAILED_PRECONDITION. */
    public static class ConfiguracaoFaltandoException extends RuntimeException {
        public ConfiguracaoFaltandoException(String mensagem) {
            super(mensagem);
        }
    }

    public ResultadoPergunta responder(PedidoPergunta pedido, Andamentos andamentos) {
        String pergunta = pedido.pergunta() == null ? "" : pedido.pergunta().strip();
        if (pergunta.isEmpty()) {
            throw new PerguntaInvalidaException("a pergunta é obrigatória");
        }
        if (pergunta.length() > PedidoPergunta.MAXIMO_CARACTERES) {
            throw new PerguntaInvalidaException(
                    "a pergunta passa de " + PedidoPergunta.MAXIMO_CARACTERES + " caracteres");
        }
        if (pedido.chaveCifrada() == null || pedido.chaveCifrada().length == 0) {
            throw new ConfiguracaoFaltandoException("este condomínio não tem chave de IA cadastrada");
        }
        ProvedorIa provedor = catalogo.porCodigo(pedido.provedor())
                .orElseThrow(() -> new ConfiguracaoFaltandoException(
                        "provedor de IA \"" + pedido.provedor() + "\" não está no catálogo deste rag"));
        ModeloIa modelo = catalogo.modeloDeRespostas(pedido.provedor(), pedido.modelo());
        if (!chaves.temPar()) {
            throw new ConfiguracaoFaltandoException(
                    "este rag está sem par de chaves: não é possível ler a chave de IA do condomínio");
        }
        String chaveApi = chaves.abrirChaveDeApi(pedido.chaveCifrada());

        andamentos.etapa(Etapa.BUSCANDO_TRECHOS, 1);
        int limite = pedido.limiteTrechos() <= 0 ? PedidoPergunta.TRECHOS_PADRAO
                : Math.min(pedido.limiteTrechos(), PedidoPergunta.TRECHOS_MAXIMO);
        BuscaDocumentos.Resultado encontrados = busca.buscar(pedido.filtros(), pergunta, pedido.modoPedido(), limite);
        Map<String, TrechoEncontrado> porId = new LinkedHashMap<>();
        encontrados.trechos().forEach(t -> porId.put(t.trechoId().toString(), t));
        String aviso = pedido.modoPedido() == BuscaDocumentos.Modo.HIBRIDA
                && encontrados.modoUsado() == BuscaDocumentos.Modo.PALAVRA
                        ? "A busca por significado está indisponível agora; a resposta usou só a busca por palavra."
                        : "";

        try (GatewayIa.Conversa conversa = gateway.abrir(parametros(provedor, modelo, chaveApi))) {
            List<PedidoPergunta.Troca> historico = pedido.historico() == null ? List.of() : pedido.historico();
            historico.stream().skip(Math.max(0, historico.size() - config.historicoMaximo()))
                    .forEach(t -> conversa.adicionarTrocaAnterior(t.pergunta(), t.resposta()));
            conversa.adicionarPergunta(InstrucoesAssistente.pergunta(pergunta, encontrados.trechos()));
            return conversar(pedido, conversa, porId, aviso, andamentos);
        }
    }

    private ResultadoPergunta conversar(PedidoPergunta pedido, GatewayIa.Conversa conversa,
            Map<String, TrechoEncontrado> porId, String aviso, Andamentos andamentos) {
        Map<String, DadoConsultado> chamadas = new LinkedHashMap<>();
        String ultimoMotivo = "";
        for (int tentativa = 1; tentativa <= TENTATIVAS; tentativa++) {
            Redacao redacao = redigir(pedido, conversa, chamadas, tentativa, andamentos);
            if (redacao.recusaDeSeguranca()) {
                // Recusa de segurança do modelo: não tenta de novo (assistente.proto)
                return naoEncontrada(conversa, pedido, tentativa, juntar(aviso,
                        "O modelo recusou responder a esta pergunta por política de segurança do provedor."));
            }
            if (redacao.motivo() != null) {
                ultimoMotivo = redacao.motivo();
            } else {
                andamentos.etapa(Etapa.VALIDANDO, tentativa);
                Optional<String> erro = validador.validar(redacao.resposta(), porId, chamadas.keySet());
                if (erro.isEmpty()) {
                    return montar(redacao.resposta(), porId, chamadas, conversa, pedido, tentativa, aviso);
                }
                ultimoMotivo = erro.get();
            }
            log.info("Resposta do assistente reprovada na tentativa {}: {}", tentativa, ultimoMotivo);
            if (tentativa < TENTATIVAS) {
                andamentos.etapa(Etapa.NOVA_TENTATIVA, tentativa + 1);
                conversa.adicionarPergunta(InstrucoesAssistente.novaTentativa(ultimoMotivo));
            }
        }
        return naoEncontrada(conversa, pedido, TENTATIVAS, aviso);
    }

    /** Uma tentativa: o laço de ferramentas até o modelo entregar o JSON (ou estourar o teto de voltas). */
    private Redacao redigir(PedidoPergunta pedido, GatewayIa.Conversa conversa,
            Map<String, DadoConsultado> chamadas, int tentativa, Andamentos andamentos) {
        for (int volta = 1; volta <= Math.max(1, config.voltasFerramentas()); volta++) {
            andamentos.etapa(Etapa.REDIGINDO, tentativa);
            Turno turno = conversa.enviar();
            if (turno.parada() == Parada.RECUSA) {
                return Redacao.recusa();
            }
            if (turno.parada() == Parada.FERRAMENTA) {
                andamentos.etapa(Etapa.CONSULTANDO_DADOS, tentativa);
                conversa.adicionarResultados(executar(pedido, turno.chamadas(), chamadas));
                continue;
            }
            if (turno.parada() == Parada.CORTADA) {
                return Redacao.motivo("a resposta passou do tamanho máximo; responda de forma mais curta");
            }
            try {
                return Redacao.pronta(EsquemaResposta.ler(turno.textoJson()));
            } catch (EsquemaResposta.RespostaIlegivelException erro) {
                return Redacao.motivo(erro.getMessage());
            }
        }
        return Redacao.motivo("o limite de consultas desta pergunta foi atingido; responda com o que já tem");
    }

    private List<ResultadoFerramenta> executar(PedidoPergunta pedido, List<ChamadaFerramenta> pedidas,
            Map<String, DadoConsultado> chamadas) {
        var backend = clienteConsulta.comToken(pedido.autorizacao());
        List<ResultadoFerramenta> resultados = new ArrayList<>();
        for (ChamadaFerramenta chamada : pedidas) {
            String chamadaId = "c" + (chamadas.size() + 1);
            try {
                DadoConsultado dado = ferramentas.executar(chamadaId, chamada.nome(), chamada.argumentos(),
                        pedido.filtros().condominioId().toString(), backend);
                chamadas.put(chamadaId, dado);
                resultados.add(new ResultadoFerramenta(chamada.id(), FerramentasNumericas.paraOModelo(dado), false));
            } catch (FerramentasNumericas.FerramentaDesconhecidaException erro) {
                resultados.add(new ResultadoFerramenta(chamada.id(), erro.getMessage(), true));
            } catch (StatusRuntimeException erro) {
                Status.Code codigo = erro.getStatus().getCode();
                if (codigo == Status.Code.UNAVAILABLE || codigo == Status.Code.DEADLINE_EXCEEDED
                        || codigo == Status.Code.UNIMPLEMENTED) {
                    // Backend fora do ar = UNAVAILABLE no Perguntar; não há como responder número nenhum
                    throw erro;
                }
                log.info("Ferramenta {} recusada pelo backend ({}): {}", chamada.nome(), codigo,
                        erro.getStatus().getDescription());
                resultados.add(new ResultadoFerramenta(chamada.id(),
                        "a consulta não foi autorizada ou os parâmetros são inválidos: "
                                + Optional.ofNullable(erro.getStatus().getDescription()).orElse(codigo.name()),
                        true));
            }
        }
        return resultados;
    }

    private ResultadoPergunta montar(RespostaModelo resposta, Map<String, TrechoEncontrado> porId,
            Map<String, DadoConsultado> chamadas, GatewayIa.Conversa conversa, PedidoPergunta pedido, int tentativas,
            String aviso) {
        if (resposta.naoEncontrado() || resposta.vazia()) {
            return naoEncontrada(conversa, pedido, tentativas, aviso, resposta.sugestao());
        }
        List<ResultadoPergunta.Paragrafo> paragrafos = new ArrayList<>();
        var citados = new LinkedHashSet<String>();
        for (ParagrafoModelo p : resposta.nosDocumentos()) {
            paragrafos.add(new ResultadoPergunta.Paragrafo(p.texto(), List.copyOf(p.trechoIds())));
            citados.addAll(p.trechoIds());
        }
        List<ResultadoPergunta.Dado> dados = new ArrayList<>();
        for (ComentarioDado d : resposta.nosDadosGravados()) {
            dados.add(new ResultadoPergunta.Dado(chamadas.get(d.chamadaId()), d.comentario()));
        }
        return new ResultadoPergunta(ResultadoPergunta.Situacao.RESPONDIDA, List.copyOf(paragrafos),
                List.copyOf(dados), citados.stream().map(porId::get).toList(), resposta.sugestao(), aviso,
                uso(conversa, pedido, tentativas));
    }

    private ResultadoPergunta naoEncontrada(GatewayIa.Conversa conversa, PedidoPergunta pedido, int tentativas,
            String aviso) {
        return naoEncontrada(conversa, pedido, tentativas, aviso, "");
    }

    private ResultadoPergunta naoEncontrada(GatewayIa.Conversa conversa, PedidoPergunta pedido, int tentativas,
            String aviso, String sugestao) {
        return new ResultadoPergunta(ResultadoPergunta.Situacao.NAO_ENCONTRADA, List.of(), List.of(), List.of(),
                sugestao == null ? "" : sugestao, aviso, uso(conversa, pedido, tentativas));
    }

    private static String juntar(String primeiro, String segundo) {
        return primeiro == null || primeiro.isBlank() ? segundo : primeiro + " " + segundo;
    }

    private static ResultadoPergunta.Uso uso(GatewayIa.Conversa conversa, PedidoPergunta pedido, int tentativas) {
        return new ResultadoPergunta.Uso(conversa.tokensEntrada(), conversa.tokensSaida(), pedido.provedor(),
                pedido.modelo(), InstrucoesAssistente.VERSAO, tentativas);
    }

    private Parametros parametros(ProvedorIa provedor, ModeloIa modelo, String chaveApi) {
        return new Parametros(provedor.tipo(), modelo.id(), chaveApi, InstrucoesAssistente.sistema(),
                config.maxTokens(), esforco(modelo.id()), EsquemaResposta.esquema(), ferramentas.definicoes());
    }

    /**
     * O Haiku 4.5 recusa {@code effort} (erro do provedor): naquele modelo vai só o formato da saída estruturada. Nos
     * modelos que aceitam, vale o configurado ({@code RAG_ESFORCO}, padrão medium).
     */
    private String esforco(String idModelo) {
        return idModelo.contains("haiku") ? "" : config.esforco();
    }

    /** Estado de uma tentativa: pronta, reprovada com motivo, ou recusa de segurança do modelo. */
    private record Redacao(RespostaModelo resposta, String motivo, boolean recusaDeSeguranca) {

        static Redacao pronta(RespostaModelo resposta) {
            return new Redacao(resposta, null, false);
        }

        static Redacao motivo(String motivo) {
            return new Redacao(null, motivo, false);
        }

        static Redacao recusa() {
            return new Redacao(null, null, true);
        }
    }
}
