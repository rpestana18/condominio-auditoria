package br.com.condominioauditoria.rag.assistente.pergunta;

import br.com.condominioauditoria.contratos.consulta.v1.ConsultaGrpc;
import br.com.condominioauditoria.rag.config.PropriedadesRag;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Cliente do gRPC {@code contracts/grpc/consulta/v1} do backend, usado pelas ferramentas numéricas do chat
 * (ADR 0003, Decisão 5.2: "o rag chama as ferramentas no backend com o token do próprio usuário"). O mesmo token que
 * chegou no metadado {@code authorization} do {@code Perguntar} é repassado; o backend aplica perfil e condomínio
 * como faz para o mcp.
 *
 * Um canal só para o serviço (endereço {@code BACKEND_GRPC}); o prazo é por chamada, para o chat nunca ficar preso.
 */
@Component
public class ClienteConsulta {

    private static final Logger log = LoggerFactory.getLogger(ClienteConsulta.class);

    private static final Metadata.Key<String> AUTORIZACAO =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final ManagedChannel canal;
    private final int prazoSegundos;

    ClienteConsulta(PropriedadesRag propriedades) {
        var config = propriedades.assistente();
        this.prazoSegundos = config.prazoFerramentaSegundos();
        this.canal = Grpc.newChannelBuilder(config.backendGrpc(), InsecureChannelCredentials.create()).build();
        log.info("Ferramentas numéricas do assistente vão para o gRPC Consulta em {}", config.backendGrpc());
    }

    /** Para os testes: canal já pronto (servidor em processo). */
    public ClienteConsulta(ManagedChannel canal, int prazoSegundos) {
        this.canal = canal;
        this.prazoSegundos = prazoSegundos;
    }

    /** Stub com o token do usuário desta pergunta e o prazo de uma chamada. */
    public ConsultaGrpc.ConsultaBlockingStub comToken(String autorizacao) {
        var cabecalhos = new Metadata();
        cabecalhos.put(AUTORIZACAO, autorizacao);
        return ConsultaGrpc.newBlockingStub(canal)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(cabecalhos))
                .withDeadlineAfter(prazoSegundos, TimeUnit.SECONDS);
    }

    @PreDestroy
    void fechar() {
        canal.shutdown();
        try {
            canal.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
        }
    }
}
