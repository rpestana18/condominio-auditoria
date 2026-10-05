package br.com.condominioauditoria.rag.grpc;

import br.com.condominioauditoria.rag.config.PropriedadesRag;
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
 * Servidor gRPC do assistente (contracts/grpc/assistente/v1), na rede interna (porta 9091). Sobe e desce junto com o
 * Spring. Quem chama é só o backend.
 *
 * Sem TLS dentro da rede do docker compose; na nuvem, o TLS fica na malha de rede ou entra aqui por parâmetro.
 *
 * O {@link AutorizacaoGrpc} guarda o metadado "authorization" no contexto: Perguntar e ListarProvedores exigem o
 * token (UNAUTHENTICATED sem ele) e Perguntar o repassa às ferramentas numéricas do backend.
 * TODO(ADR 0003, Decisão 5.2 e contrato assistente.proto): conferir a assinatura do JWT como o AutenticacaoGrpc do
 * backend, com spring-boot-starter-oauth2-resource-server. Hoje o rag confia no backend (rede interna), que já
 * verificou token, perfil, condomínio e módulo antes de chamar.
 */
@Component
class ServidorGrpc implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ServidorGrpc.class);

    private final AssistenteGrpcServico assistente;
    private final int porta;
    private Server servidor;

    ServidorGrpc(AssistenteGrpcServico assistente, PropriedadesRag propriedades) {
        this.assistente = assistente;
        this.porta = propriedades.grpc().porta();
    }

    @Override
    public void start() {
        try {
            servidor = Grpc.newServerBuilderForPort(porta, InsecureServerCredentials.create())
                    .addService(io.grpc.ServerInterceptors.intercept(assistente, new AutorizacaoGrpc()))
                    .build()
                    .start();
            log.info("Servidor gRPC do assistente ouvindo na porta {}", porta);
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
    }

    @Override
    public boolean isRunning() {
        return servidor != null;
    }
}
