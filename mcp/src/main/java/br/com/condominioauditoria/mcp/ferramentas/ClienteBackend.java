package br.com.condominioauditoria.mcp.ferramentas;

import br.com.condominioauditoria.contratos.consulta.v1.ConsultaGrpc;
import br.com.condominioauditoria.mcp.config.McpConfig;
import br.com.condominioauditoria.mcp.config.PropriedadesMcp;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Cria o stub gRPC do backend já com o token do usuário e o prazo da chamada. */
@Component
class ClienteBackend {

    private static final Metadata.Key<String> AUTORIZACAO =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final ManagedChannel canal;
    private final int prazoSegundos;

    ClienteBackend(ManagedChannel canal, PropriedadesMcp propriedades) {
        this.canal = canal;
        this.prazoSegundos = propriedades.backend().prazoSegundos();
    }

    ConsultaGrpc.ConsultaBlockingStub consulta(McpTransportContext contexto) {
        Object token = contexto == null ? null : contexto.get(McpConfig.AUTORIZACAO);
        if (token == null) {
            throw new IllegalStateException("Chamada sem token: configure o cabeçalho Authorization no cliente MCP");
        }
        var cabecalhos = new Metadata();
        cabecalhos.put(AUTORIZACAO, token.toString());
        return ConsultaGrpc.newBlockingStub(canal)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(cabecalhos))
                .withDeadlineAfter(prazoSegundos, TimeUnit.SECONDS);
    }

    /** Erro do backend em português, para a IA repassar ao usuário. O backend já manda a descrição em português. */
    static RuntimeException traduzir(StatusRuntimeException erro) {
        String descricao = erro.getStatus().getDescription();
        return switch (erro.getStatus().getCode()) {
            case UNAUTHENTICATED -> new IllegalStateException(Objects.requireNonNullElse(descricao, "Token recusado"));
            case PERMISSION_DENIED -> new IllegalStateException(Objects.requireNonNullElse(descricao, "Sem acesso"));
            case NOT_FOUND -> new IllegalArgumentException(Objects.requireNonNullElse(descricao, "Não encontrado"));
            case INVALID_ARGUMENT -> new IllegalArgumentException(Objects.requireNonNullElse(descricao, "Pedido inválido"));
            case UNAVAILABLE, DEADLINE_EXCEEDED -> new IllegalStateException("Backend indisponível no momento");
            default -> new IllegalStateException("Erro no backend");
        };
    }
}
