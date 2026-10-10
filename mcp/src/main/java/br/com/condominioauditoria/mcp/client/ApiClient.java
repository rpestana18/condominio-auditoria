package br.com.condominioauditoria.mcp.client;

import br.com.condominioauditoria.contratos.consulta.v1.ConsultaGrpc;
import br.com.condominioauditoria.mcp.config.McpConfig;
import br.com.condominioauditoria.mcp.config.properties.McpProperties;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Creates the api's gRPC stub already carrying the user's token and the call deadline. */
@Component
public class ApiClient {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final ManagedChannel channel;
    private final int timeoutSeconds;

    public ApiClient(ManagedChannel channel, McpProperties properties) {
        this.channel = channel;
        this.timeoutSeconds = properties.api().timeoutSeconds();
    }

    public ConsultaGrpc.ConsultaBlockingStub query(McpTransportContext context) {
        Object token = context == null ? null : context.get(McpConfig.AUTHORIZATION);
        if (token == null) {
            throw new IllegalStateException("Chamada sem token: configure o cabeçalho Authorization no cliente MCP");
        }
        var headers = new Metadata();
        headers.put(AUTHORIZATION, token.toString());
        return ConsultaGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                .withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * api error in Portuguese, for the AI to pass on to the user. The api already sends the description in Portuguese.
     */
    public static RuntimeException translate(StatusRuntimeException error) {
        String description = error.getStatus().getDescription();
        return switch (error.getStatus().getCode()) {
            case UNAUTHENTICATED -> new IllegalStateException(Objects.requireNonNullElse(description,
                    "Token recusado"));
            case PERMISSION_DENIED -> new IllegalStateException(Objects.requireNonNullElse(description, "Sem acesso"));
            case NOT_FOUND -> new IllegalArgumentException(Objects.requireNonNullElse(description, "Não encontrado"));
            case INVALID_ARGUMENT -> new IllegalArgumentException(Objects.requireNonNullElse(description,
                    "Pedido inválido"));
            case FAILED_PRECONDITION -> new IllegalStateException(
                    Objects.requireNonNullElse(description, "Operação não disponível para este condomínio"));
            // api of an older version, without the rpc (e.g. BuscarDocumentos)
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
