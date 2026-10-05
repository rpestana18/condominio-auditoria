package br.com.condominioauditoria.rag.assistente.gateway;

import br.com.condominioauditoria.rag.assistente.gateway.ContratoModelo.ChamadaFerramenta;
import br.com.condominioauditoria.rag.assistente.gateway.ContratoModelo.DefinicaoFerramenta;
import br.com.condominioauditoria.rag.assistente.gateway.ContratoModelo.ErroProvedorException;
import br.com.condominioauditoria.rag.assistente.gateway.ContratoModelo.Parada;
import br.com.condominioauditoria.rag.assistente.gateway.ContratoModelo.Parametros;
import br.com.condominioauditoria.rag.assistente.gateway.ContratoModelo.ResultadoFerramenta;
import br.com.condominioauditoria.rag.assistente.gateway.ContratoModelo.Turno;
import br.com.condominioauditoria.rag.config.PropriedadesRag;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicSetup;

/**
 * Conversa com o Claude pelo SDK oficial {@code com.anthropic:anthropic-java} (transitivo do starter aprovado na
 * ADR 0003, {@code spring-ai-starter-model-anthropic}). O cliente é montado por
 * {@link AnthropicSetup#setupSyncClient} — o único caminho público do starter para obter um
 * {@link AnthropicClient} com chave e endereço escolhidos em tempo de execução.
 *
 * Decisões que o modelo exige (claude-sonnet-5-5):
 * <ul>
 * <li>pensamento adaptativo é o padrão: nada de {@code thinking} desligado nem de orçamento de tokens (daria 400);</li>
 * <li>{@code tool_choice} forçado daria 400: fica em automático (nem é enviado);</li>
 * <li>sem prefill de assistente;</li>
 * <li>saída estruturada em {@code output_config.format}, que funciona junto com ferramentas;</li>
 * <li>{@code output_config.effort} configurável; no Haiku 4.5 fica vazio, porque aquele modelo recusa effort.</li>
 * </ul>
 *
 * A chave de API nunca é registrada em log; só é passada ao cliente desta conversa.
 */
final class ConversaAnthropic implements GatewayIa.Conversa {

    private static final Logger log = LoggerFactory.getLogger(ConversaAnthropic.class);

    private final AnthropicClient cliente;
    private final Parametros parametros;
    private final List<MessageParam> mensagens = new ArrayList<>();
    private long tokensEntrada;
    private long tokensSaida;

    ConversaAnthropic(Parametros parametros, PropriedadesRag.Assistente config) {
        this.parametros = parametros;
        this.cliente = AnthropicSetup.setupSyncClient(config.anthropicUrl(), parametros.chaveApi(),
                Duration.ofSeconds(config.prazoProvedorSegundos()), config.tentativasProvedor(), null, Map.of());
    }

    @Override
    public void adicionarPergunta(String texto) {
        mensagens.add(MessageParam.builder().role(MessageParam.Role.USER).content(texto).build());
    }

    @Override
    public void adicionarTrocaAnterior(String pergunta, String resposta) {
        mensagens.add(MessageParam.builder().role(MessageParam.Role.USER).content(pergunta).build());
        mensagens.add(MessageParam.builder().role(MessageParam.Role.ASSISTANT).content(resposta).build());
    }

    @Override
    public void adicionarResultados(List<ResultadoFerramenta> resultados) {
        List<ContentBlockParam> blocos = resultados.stream()
                .map(r -> ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(r.id())
                        .content(r.conteudo())
                        .isError(r.erro())
                        .build()))
                .toList();
        mensagens.add(MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(blocos).build());
    }

    @Override
    public Turno enviar() {
        Message resposta;
        try {
            resposta = cliente.messages().create(montar());
        } catch (AnthropicServiceException erro) {
            throw traduzir(erro);
        } catch (AnthropicIoException erro) {
            throw new ErroProvedorException(ErroProvedorException.Tipo.INDISPONIVEL,
                    "não foi possível falar com o provedor de IA", erro);
        }
        tokensEntrada += resposta.usage().inputTokens();
        tokensSaida += resposta.usage().outputTokens();
        // A resposta do modelo volta para a conversa como está (inclusive os blocos de ferramenta), sem prefill
        mensagens.add(resposta.toParam());
        return interpretar(resposta);
    }

    private MessageCreateParams montar() {
        var construtor = MessageCreateParams.builder()
                .model(parametros.modelo())
                .maxTokens(parametros.maxTokens())
                .system(parametros.instrucoes())
                .messages(List.copyOf(mensagens))
                .outputConfig(saida());
        for (DefinicaoFerramenta f : parametros.ferramentas()) {
            construtor.addTool(ferramenta(f));
        }
        return construtor.build();
    }

    private OutputConfig saida() {
        var construtor = OutputConfig.builder().format(JsonOutputFormat.builder()
                .schema(JsonOutputFormat.Schema.builder()
                        .additionalProperties(converter(parametros.esquemaSaida()))
                        .build())
                .build());
        if (parametros.esforco() != null && !parametros.esforco().isBlank()) {
            construtor.effort(OutputConfig.Effort.of(parametros.esforco()));
        }
        return construtor.build();
    }

    private static Tool ferramenta(DefinicaoFerramenta definicao) {
        Map<String, Object> esquema = definicao.esquemaEntrada();
        var propriedades = Tool.InputSchema.Properties.builder();
        objeto(esquema.get("properties")).forEach((nome, valor) -> propriedades.putAdditionalProperty(nome,
                JsonValue.from(valor)));
        var entrada = Tool.InputSchema.builder().properties(propriedades.build());
        Object obrigatorios = esquema.get("required");
        if (obrigatorios instanceof List<?> lista) {
            entrada.required(lista.stream().map(String::valueOf).toList());
        }
        return Tool.builder()
                .name(definicao.nome())
                .description(definicao.descricao())
                .inputSchema(entrada.build())
                .build();
    }

    private Turno interpretar(Message resposta) {
        Optional<StopReason> parada = resposta.stopReason();
        if (parada.filter(StopReason.REFUSAL::equals).isPresent()) {
            String explicacao = resposta.stopDetails().flatMap(d -> d.explanation()).orElse("");
            return new Turno(Parada.RECUSA, "", List.of(), explicacao);
        }
        List<ChamadaFerramenta> chamadas = new ArrayList<>();
        StringBuilder texto = new StringBuilder();
        for (ContentBlock bloco : resposta.content()) {
            bloco.text().ifPresent(t -> texto.append(t.text()));
            bloco.toolUse().ifPresent(t -> chamadas.add(new ChamadaFerramenta(t.id(), t.name(), argumentos(t))));
        }
        if (!chamadas.isEmpty()) {
            return new Turno(Parada.FERRAMENTA, texto.toString(), List.copyOf(chamadas), "");
        }
        if (parada.filter(p -> StopReason.MAX_TOKENS.equals(p)
                || StopReason.MODEL_CONTEXT_WINDOW_EXCEEDED.equals(p)).isPresent()) {
            return new Turno(Parada.CORTADA, texto.toString(), List.of(), "");
        }
        return new Turno(Parada.FIM, texto.toString(), List.of(), "");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> argumentos(com.anthropic.models.messages.ToolUseBlock bloco) {
        try {
            Map<String, Object> lido = bloco._input().convert(Map.class);
            return lido == null ? Map.of() : new LinkedHashMap<>(lido);
        } catch (RuntimeException erro) {
            log.warn("Argumentos da ferramenta {} não puderam ser lidos: {}", bloco.name(), erro.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objeto(Object valor) {
        return valor instanceof Map<?, ?> mapa ? (Map<String, Object>) mapa : Map.of();
    }

    private static Map<String, JsonValue> converter(Map<String, Object> esquema) {
        Map<String, JsonValue> saida = new LinkedHashMap<>();
        esquema.forEach((chave, valor) -> saida.put(chave, JsonValue.from(valor)));
        return saida;
    }

    /** Erros do provedor para os status gRPC da especificação (assistente.proto, lista de erros de Perguntar). */
    private static ErroProvedorException traduzir(AnthropicServiceException erro) {
        int codigo = erro.statusCode();
        if (codigo == 401 || codigo == 403) {
            return new ErroProvedorException(ErroProvedorException.Tipo.CHAVE_RECUSADA,
                    "a chave de API do condomínio foi recusada pelo provedor", erro);
        }
        if (codigo == 429) {
            return new ErroProvedorException(ErroProvedorException.Tipo.LIMITE,
                    "limite de uso do provedor de IA atingido", erro);
        }
        if (codigo >= 500) {
            return new ErroProvedorException(ErroProvedorException.Tipo.INDISPONIVEL,
                    "o provedor de IA respondeu erro " + codigo, erro);
        }
        return new ErroProvedorException(ErroProvedorException.Tipo.PEDIDO_INVALIDO,
                "o provedor de IA recusou o pedido (erro " + codigo + ")", erro);
    }

    @Override
    public long tokensEntrada() {
        return tokensEntrada;
    }

    @Override
    public long tokensSaida() {
        return tokensSaida;
    }

    @Override
    public void close() {
        mensagens.clear();
        try {
            cliente.close();
        } catch (RuntimeException erro) {
            log.debug("Falha ao fechar o cliente do provedor: {}", erro.getMessage());
        }
    }
}
