package br.com.condominioauditoria.api.grpc.server;

import br.com.condominioauditoria.api.config.properties.ApiProperties;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * The api's gRPC server, on the internal network (port 9090). Starts and stops with Spring.
 *
 * No TLS inside the docker compose network; in the cloud, TLS lives in the service mesh or comes in here by parameter.
 * Authentication always applies: every call carries the user's token (see {@link GrpcAuthInterceptor}). Callers: the
 * mcp (tools of the user's Claude) and the rag (numeric tools of the chat, with the token of the user who asked).
 *
 * Calls run on their own thread pool ("grpc-servidor-N"), apart from the REST API threads (Tomcat): during a chat
 * question the REST thread waits for the rag, and the rag calls Consulta back here; with separate pools, that round
 * trip never waits for a thread busy with the question itself (ADR 0003, Decision 5.2).
 */
@Component
class GrpcServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GrpcServer.class);

    private final QueryGrpcService query;
    private final GrpcAuthInterceptor authInterceptor;
    private final int port;
    private Server server;
    private ExecutorService threads;

    GrpcServer(QueryGrpcService query, GrpcAuthInterceptor authentication, ApiProperties properties) {
        this.query = query;
        this.authInterceptor = authentication;
        this.port = properties.grpc().port();
    }

    @Override
    public void start() {
        threads = Executors.newCachedThreadPool(Thread.ofPlatform().name("grpc-servidor-", 1).daemon(true).factory());
        try {
            server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
                    .executor(threads)
                    .addService(ServerInterceptors.intercept(query, authInterceptor))
                    .build()
                    .start();
            log.info("Servidor gRPC ouvindo na porta {}", port);
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
        if (threads != null) {
            threads.shutdownNow();
            threads = null;
        }
    }

    @Override
    public boolean isRunning() {
        return server != null;
    }

    int port() {
        return server == null ? port : server.getPort();
    }
}
