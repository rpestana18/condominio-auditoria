package br.com.condominioauditoria.rag.grpc;

import br.com.condominioauditoria.rag.config.properties.RagProperties;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * gRPC server of the assistant (contracts/grpc/assistant/v2), on the internal network (port 9091). Starts and stops
 * together with Spring. Only the api calls it.
 *
 * No TLS inside the docker compose network; in the cloud, TLS lives in the service mesh or comes in here as a
 * parameter.
 *
 * {@link GrpcAuthorization} keeps the "authorization" metadata in the context: Ask and ListProviders require
 * the token (UNAUTHENTICATED without it) and Ask passes it on to the api's numeric tools.
 * TODO(ADR 0003, Decision 5.2 and contract assistant.proto): check the JWT signature like the api's gRPC
 * authentication, with spring-boot-starter-oauth2-resource-server. Today the rag trusts the api (internal network),
 * which has already checked token, role, condominium and module before calling.
 */
@Component
class GrpcServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GrpcServer.class);

    private final AssistantGrpcService assistant;
    private final int port;
    private Server server;

    GrpcServer(AssistantGrpcService assistant, RagProperties properties) {
        this.assistant = assistant;
        this.port = properties.grpc().port();
    }

    @Override
    public void start() {
        try {
            server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
                    .addService(io.grpc.ServerInterceptors.intercept(assistant, new GrpcAuthorization()))
                    .build()
                    .start();
            log.info("Servidor gRPC do assistente ouvindo na porta {}", port);
        } catch (IOException error) {
            throw new UncheckedIOException("Não foi possível abrir a porta gRPC " + port, error);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.shutdown();
            try {
                server.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            server = null;
        }
    }

    @Override
    public boolean isRunning() {
        return server != null;
    }
}
