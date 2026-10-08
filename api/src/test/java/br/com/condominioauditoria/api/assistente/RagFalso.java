package br.com.condominioauditoria.api.assistente;

import br.com.condominioauditoria.api.config.PropriedadesCondominio;
import br.com.condominioauditoria.api.grpc.ClienteAssistente;
import br.com.condominioauditoria.contratos.assistente.v1.AssistenteGrpc;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarRequest;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarResponse;
import br.com.condominioauditoria.contratos.assistente.v1.PerguntarEvento;
import br.com.condominioauditoria.contratos.assistente.v1.PerguntarRequest;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/** rag falso em processo: guarda o que recebeu e responde como o teste mandar. */
class RagFalso implements AutoCloseable {

    static final Metadata.Key<String> AUTORIZACAO = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    final List<PerguntarRequest> perguntas = new CopyOnWriteArrayList<>();
    final List<BuscarRequest> buscas = new CopyOnWriteArrayList<>();
    final AtomicReference<String> token = new AtomicReference<>();
    volatile BiConsumer<PerguntarRequest, StreamObserver<PerguntarEvento>> aoPerguntar = (p, r) -> r.onCompleted();
    volatile BiConsumer<BuscarRequest, StreamObserver<BuscarResponse>> aoBuscar = (p, r) -> {
        r.onNext(BuscarResponse.getDefaultInstance());
        r.onCompleted();
    };

    private final Server servidor;
    private final ManagedChannel canal;
    final ClienteAssistente cliente;

    RagFalso() throws Exception {
        var servico = new AssistenteGrpc.AssistenteImplBase() {
            @Override
            public void perguntar(PerguntarRequest pedido, StreamObserver<PerguntarEvento> resposta) {
                perguntas.add(pedido);
                aoPerguntar.accept(pedido, resposta);
            }

            @Override
            public void buscar(BuscarRequest pedido, StreamObserver<BuscarResponse> resposta) {
                buscas.add(pedido);
                aoBuscar.accept(pedido, resposta);
            }
        };
        var capturaToken = new ServerInterceptor() {
            @Override
            public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> chamada, Metadata cabecalhos,
                    ServerCallHandler<Q, R> proximo) {
                token.set(cabecalhos.get(AUTORIZACAO));
                return proximo.startCall(chamada, cabecalhos);
            }
        };
        String nome = InProcessServerBuilder.generateName();
        servidor = InProcessServerBuilder.forName(nome).addService(ServerInterceptors.intercept(servico, capturaToken))
                .build().start();
        canal = InProcessChannelBuilder.forName(nome).build();
        cliente = new ClienteAssistente(canal, new PropriedadesCondominio(null, null, null,
                new PropriedadesCondominio.Rag("rag:9091", 5, 7, 300)));
    }

    void desligar() {
        servidor.shutdownNow();
    }

    @Override
    public void close() {
        canal.shutdownNow();
        servidor.shutdownNow();
    }
}
