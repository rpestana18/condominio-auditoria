package br.com.condominioauditoria.rag.client;

import br.com.condominioauditoria.rag.client.ModelContract.Parameters;
import br.com.condominioauditoria.rag.client.ModelContract.ProviderErrorException;
import br.com.condominioauditoria.rag.client.ModelContract.Stop;
import br.com.condominioauditoria.rag.client.ModelContract.ToolCall;
import br.com.condominioauditoria.rag.client.ModelContract.ToolDefinition;
import br.com.condominioauditoria.rag.client.ModelContract.ToolResult;
import br.com.condominioauditoria.rag.client.ModelContract.Turn;
import br.com.condominioauditoria.rag.config.properties.RagProperties;
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
 * Conversation with Claude through the official SDK {@code com.anthropic:anthropic-java} (transitive from the starter
 * approved in ADR 0003, {@code spring-ai-starter-model-anthropic}). The client is built by
 * {@link AnthropicSetup#setupSyncClient}, the starter's only public way to get an {@link AnthropicClient} with key and
 * address chosen at runtime.
 *
 * Decisions the model requires (claude-sonnet-5-5):
 * <ul>
 * <li>adaptive thinking is the default: no disabled {@code thinking} and no token budget (would give 400);</li>
 * <li>a forced {@code tool_choice} would give 400: it stays automatic (not even sent);</li>
 * <li>no assistant prefill;</li>
 * <li>structured output in {@code output_config.format}, which works together with tools;</li>
 * <li>configurable {@code output_config.effort}; empty on Haiku 4.5, because that model rejects effort.</li>
 * </ul>
 *
 * The API key is never logged; it is only passed to the client of this conversation.
 */
final class AnthropicConversation implements AiGateway.Conversation {

    private static final Logger log = LoggerFactory.getLogger(AnthropicConversation.class);

    private final AnthropicClient client;
    private final Parameters parameters;
    private final List<MessageParam> messages = new ArrayList<>();
    private long inputTokens;
    private long outputTokens;

    AnthropicConversation(Parameters params, RagProperties.Assistant config) {
        this.parameters = params;
        this.client = AnthropicSetup.setupSyncClient(config.anthropicUrl(), params.apiKey(),
                Duration.ofSeconds(config.providerTimeoutSeconds()), config.providerAttempts(), null, Map.of());
    }

    @Override
    public void addQuestion(String text) {
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(text).build());
    }

    @Override
    public void addPreviousExchange(String question, String response) {
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(question).build());
        messages.add(MessageParam.builder().role(MessageParam.Role.ASSISTANT).content(response).build());
    }

    @Override
    public void addResults(List<ToolResult> results) {
        List<ContentBlockParam> blocks = results.stream()
                .map(r -> ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(r.id())
                        .content(r.content())
                        .isError(r.error())
                        .build()))
                .toList();
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(blocks).build());
    }

    @Override
    public Turn send() {
        Message response;
        try {
            response = client.messages().create(build());
        } catch (AnthropicServiceException error) {
            throw translate(error);
        } catch (AnthropicIoException error) {
            throw new ProviderErrorException(ProviderErrorException.Kind.UNAVAILABLE,
                    "não foi possível falar com o provedor de IA", error);
        }
        inputTokens += response.usage().inputTokens();
        outputTokens += response.usage().outputTokens();
        // The model's response goes back into the conversation as is (including the tool blocks), with no prefill
        messages.add(response.toParam());
        return interpret(response);
    }

    private MessageCreateParams build() {
        var constructor = MessageCreateParams.builder()
                .model(parameters.model())
                .maxTokens(parameters.maxTokens())
                .system(parameters.instructions())
                .messages(List.copyOf(messages))
                .outputConfig(output());
        for (ToolDefinition f : parameters.tools()) {
            constructor.addTool(tool(f));
        }
        return constructor.build();
    }

    private OutputConfig output() {
        var constructor = OutputConfig.builder().format(JsonOutputFormat.builder()
                .schema(JsonOutputFormat.Schema.builder()
                        .additionalProperties(convert(parameters.outputSchema()))
                        .build())
                .build());
        if (parameters.effort() != null && !parameters.effort().isBlank()) {
            constructor.effort(OutputConfig.Effort.of(parameters.effort()));
        }
        return constructor.build();
    }

    private static Tool tool(ToolDefinition definition) {
        Map<String, Object> schema = definition.inputSchema();
        var properties = Tool.InputSchema.Properties.builder();
        object(schema.get("properties")).forEach((name, value) -> properties.putAdditionalProperty(name,
                JsonValue.from(value)));
        var input = Tool.InputSchema.builder().properties(properties.build());
        Object required = schema.get("required");
        if (required instanceof List<?> list) {
            input.required(list.stream().map(String::valueOf).toList());
        }
        return Tool.builder()
                .name(definition.name())
                .description(definition.description())
                .inputSchema(input.build())
                .build();
    }

    private Turn interpret(Message response) {
        Optional<StopReason> stopReason = response.stopReason();
        if (stopReason.filter(StopReason.REFUSAL::equals).isPresent()) {
            String explanation = response.stopDetails().flatMap(d -> d.explanation()).orElse("");
            return new Turn(Stop.REFUSAL, "", List.of(), explanation);
        }
        List<ToolCall> calls = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : response.content()) {
            block.text().ifPresent(t -> text.append(t.text()));
            block.toolUse().ifPresent(t -> calls.add(new ToolCall(t.id(), t.name(), arguments(t))));
        }
        if (!calls.isEmpty()) {
            return new Turn(Stop.TOOL_USE, text.toString(), List.copyOf(calls), "");
        }
        if (stopReason.filter(p -> StopReason.MAX_TOKENS.equals(p)
                || StopReason.MODEL_CONTEXT_WINDOW_EXCEEDED.equals(p)).isPresent()) {
            return new Turn(Stop.TRUNCATED, text.toString(), List.of(), "");
        }
        return new Turn(Stop.END, text.toString(), List.of(), "");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> arguments(com.anthropic.models.messages.ToolUseBlock block) {
        try {
            Map<String, Object> read = block._input().convert(Map.class);
            return read == null ? Map.of() : new LinkedHashMap<>(read);
        } catch (RuntimeException error) {
            log.warn("Argumentos da ferramenta {} não puderam ser lidos: {}", block.name(), error.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static Map<String, JsonValue> convert(Map<String, Object> schema) {
        Map<String, JsonValue> output = new LinkedHashMap<>();
        schema.forEach((key, value) -> output.put(key, JsonValue.from(value)));
        return output;
    }

    /** Provider errors to the gRPC statuses of the specification (assistant.proto, Ask error list). */
    private static ProviderErrorException translate(AnthropicServiceException error) {
        int code = error.statusCode();
        if (code == 401 || code == 403) {
            return new ProviderErrorException(ProviderErrorException.Kind.KEY_REJECTED,
                    "a chave de API do condomínio foi recusada pelo provedor", error);
        }
        if (code == 429) {
            return new ProviderErrorException(ProviderErrorException.Kind.RATE_LIMITED,
                    "limite de uso do provedor de IA atingido", error);
        }
        if (code >= 500) {
            return new ProviderErrorException(ProviderErrorException.Kind.UNAVAILABLE,
                    "o provedor de IA respondeu erro " + code, error);
        }
        return new ProviderErrorException(ProviderErrorException.Kind.INVALID_REQUEST,
                "o provedor de IA recusou o pedido (erro " + code + ")", error);
    }

    @Override
    public long inputTokens() {
        return inputTokens;
    }

    @Override
    public long outputTokens() {
        return outputTokens;
    }

    @Override
    public void close() {
        messages.clear();
        try {
            client.close();
        } catch (RuntimeException error) {
            log.debug("Falha ao fechar o cliente do provedor: {}", error.getMessage());
        }
    }
}
