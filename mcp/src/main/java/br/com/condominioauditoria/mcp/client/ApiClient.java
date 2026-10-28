package br.com.condominioauditoria.mcp.client;

import br.com.condominioauditoria.contracts.query.v2.QueryGrpc;
import br.com.condominioauditoria.logging.RequestIdGrpc;
import br.com.condominioauditoria.mcp.config.McpConfig;
import br.com.condominioauditoria.mcp.config.properties.McpProperties;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Creates the api's gRPC stub already carrying the user's token, the request id of the tool call (metadata
 * x-request-id, ADR 0008) and the call deadline.
 */
@Component
public class ApiClient {

    private static final Logger log = LoggerFactory.getLogger(ApiClient.class);

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final ManagedChannel channel;
    private final int timeoutSeconds;

    public ApiClient(ManagedChannel channel, McpProperties properties) {
        this.channel = channel;
        this.timeoutSeconds = properties.api().timeoutSeconds();
    }

    public QueryGrpc.QueryBlockingStub query(McpTransportContext context) {
        Object token = context == null ? null : context.get(McpConfig.AUTHORIZATION);
        if (token == null) {
            throw new IllegalStateException("Chamada sem token: configure o cabeçalho Authorization no cliente MCP");
        }
        var headers = new Metadata();
        headers.put(AUTHORIZATION, token.toString());
        return QueryGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers), RequestIdGrpc.client())
                .withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * api error in Portuguese, for the AI to pass on to the user. The api already sends the description in Portuguese.
     * Logged once here (ADR 0008, C1): a refusal of the api is one WARN line; the api out of reach (UNAVAILABLE with a
     * cause, or the deadline) is a WARN with the cause, which the MCP client never sees; anything else is an ERROR.
     */
    public static RuntimeException translate(StatusRuntimeException error) {
        String description = error.getStatus().getDescription();
        Status.Code code = error.getStatus().getCode();
        switch (code) {
            case UNAUTHENTICATED, PERMISSION_DENIED, NOT_FOUND, INVALID_ARGUMENT, FAILED_PRECONDITION, UNIMPLEMENTED ->
                    log.warn("api recusou a chamada: {} {}", code, Objects.requireNonNullElse(description, ""));
            case UNAVAILABLE, DEADLINE_EXCEEDED -> log.warn("api indisponível: {} {}", code,
                    Objects.requireNonNullElse(description, ""), error.getCause() == null ? null : error);
            default -> log.error("Erro inesperado na chamada à api: {}", code, error);
        }
        return switch (error.getStatus().getCode()) {
            case UNAUTHENTICATED -> new IllegalStateException(Objects.requireNonNullElse(description,
                    "Token recusado"));
            case PERMISSION_DENIED -> new IllegalStateException(Objects.requireNonNullElse(description, "Sem acesso"));
            case NOT_FOUND -> new IllegalArgumentException(Objects.requireNonNullElse(description, "Não encontrado"));
            case INVALID_ARGUMENT -> new IllegalArgumentException(Objects.requireNonNullElse(description,
                    "Pedido inválido"));
            case FAILED_PRECONDITION -> new IllegalStateException(
                    Objects.requireNonNullElse(description, "Operação não disponível para este condomínio"));
            // api of an older version, without the rpc (e.g. SearchDocuments)
            case UNIMPLEMENTED -> new IllegalStateException("Função indisponível nesta versão do backend");
            // With a cause = connection failure here in the mcp (technical description, in English). Without a cause =
            // the api itself answered UNAVAILABLE with the explanation in Portuguese (e.g. rag down in document
            // search).
            case UNAVAILABLE -> new IllegalStateException(error.getCause() == null && description != null
                    ? description : "Backend indisponível no momento");
            case DEADLINE_EXCEEDED -> new IllegalStateException("Backend indisponível no momento");
            default -> new IllegalStateException("Erro no backend");
        };
    }
}
