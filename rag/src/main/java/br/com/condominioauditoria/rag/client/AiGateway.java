package br.com.condominioauditoria.rag.client;

import br.com.condominioauditoria.rag.client.ModelContract.Parameters;
import br.com.condominioauditoria.rag.client.ModelContract.ToolResult;
import br.com.condominioauditoria.rag.client.ModelContract.Turn;
import br.com.condominioauditoria.rag.config.properties.RagProperties;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The only point of the system that talks to an AI model (RF-09.3, "ai-gateway do rag"). The caller passes
 * {@link Parameters} with the API key already opened and receives neutral {@link Turn}s; the rest of the rag knows no
 * provider SDK.
 *
 * In the cloud phase, this is where personal data masking goes, before any text leaves.
 */
@Component
public class AiGateway {

    private final RagProperties.Assistant config;

    public AiGateway(RagProperties properties) {
        this.config = properties.assistant();
    }

    /**
     * Opens a conversation with the provider. The HTTP client is created here, for this conversation, with the
     * condominium's key; nothing is reused across condominiums and nothing of the key stays in memory after
     * {@code close()}.
     */
    public Conversation open(Parameters params) {
        return switch (params.providerType()) {
            case "anthropic" -> new AnthropicConversation(params, config);
            default -> throw new IllegalStateException(
                    "tipo de provedor de respostas não implementado neste rag: " + params.providerType());
        };
    }

    /** Ongoing conversation with the model: the tool loop lives outside, this only goes back and forth. */
    public interface Conversation extends AutoCloseable {

        /** Adds a user message (the built question, or the reason for the retry). */
        void addQuestion(String text);

        /** Adds a previous exchange of the conversation (frontend history). */
        void addPreviousExchange(String question, String response);

        /** Returns to the model the result of the tools requested in the last turn. */
        void addResults(List<ToolResult> results);

        /** One call to the provider. Throws {@link ModelContract.ProviderErrorException} on failure. */
        Turn send();

        /** Summed over all calls of this conversation. */
        long inputTokens();

        long outputTokens();

        @Override
        void close();
    }
}
