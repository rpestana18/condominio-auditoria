package br.com.condominioauditoria.api.grpc;

import br.com.condominioauditoria.api.config.PropriedadesCondominio;
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
 * Servidor gRPC do backend, na rede interna (porta 9090). Sobe e desce junto com o Spring.
 *
 * Sem TLS dentro da rede do docker compose; na nuvem, o TLS fica na malha de rede ou entra aqui por parâmetro.
 * A autenticação vale sempre: toda chamada traz o token do usuário (ver {@link AutenticacaoGrpc}). Quem chama: o mcp
 * (ferramentas do Claude do usuário) e o rag (ferramentas numéricas do chat, com o token do usuário que perguntou).
 *
 * As chamadas rodam num grupo de threads próprio ("grpc-servidor-N"), separado das threads da API REST (Tomcat):
 * durante uma pergunta do chat, a thread REST fica esperando o rag, e o rag chama a Consulta de volta aqui; com grupos
 * separados, essa ida e volta nunca espera por uma thread ocupada com a própria pergunta (ADR 0003, Decisão 5.2).
 */
@Component
class ServidorGrpc implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ServidorGrpc.class);

    private final ConsultaGrpcServico consulta;
    private final AutenticacaoGrpc autenticacao;
    private final int porta;
    private Server servidor;
    private ExecutorService threads;

    ServidorGrpc(ConsultaGrpcServico consulta, AutenticacaoGrpc autenticacao, PropriedadesCondominio propriedades) {
        this.consulta = consulta;
        this.autenticacao = autenticacao;
        this.porta = propriedades.grpc().porta();
    }

    @Override
    public void start() {
        threads = Executors.newCachedThreadPool(Thread.ofPlatform().name("grpc-servidor-", 1).daemon(true).factory());
        try {
            servidor = Grpc.newServerBuilderForPort(porta, InsecureServerCredentials.create())
                    .executor(threads)
                    .addService(ServerInterceptors.intercept(consulta, autenticacao))
                    .build()
                    .start();
            log.info("Servidor gRPC ouvindo na porta {}", porta);
        } catch (IOException erro) {
            throw new UncheckedIOException("Não foi possível abrir a porta gRPC " + porta, erro);
        }
    }

    @Override
    public void stop() {
        if (servidor != null) {
            servidor.shutdown();
            try {
                servidor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            servidor = null;
        }
        if (threads != null) {
            threads.shutdownNow();
            threads = null;
        }
    }

    @Override
    public boolean isRunning() {
        return servidor != null;
    }

    int porta() {
        return servidor == null ? porta : servidor.getPort();
    }
}
