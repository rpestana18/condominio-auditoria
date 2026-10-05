package br.com.condominioauditoria.rag.assistente.pergunta;

import br.com.condominioauditoria.contratos.consulta.v1.ArquivoResumo;
import br.com.condominioauditoria.contratos.consulta.v1.Conferencia;
import br.com.condominioauditoria.contratos.consulta.v1.ConferenciasDoArquivoRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ConferenciasDoArquivoResponse;
import br.com.condominioauditoria.contratos.consulta.v1.ConsultaGrpc;
import br.com.condominioauditoria.contratos.consulta.v1.FundoNoPeriodo;
import br.com.condominioauditoria.contratos.consulta.v1.Lancamento;
import br.com.condominioauditoria.contratos.consulta.v1.ListarArquivosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ListarArquivosResponse;
import br.com.condominioauditoria.contratos.consulta.v1.ListarLancamentosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ResumoFundosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ResumoFundosResponse;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;

/**
 * Servidor gRPC falso do contrato de consulta do backend, com valores exatos em centavos. Também guarda o token
 * recebido, para o teste conferir que o rag repassa o MESMO token do pedido (ADR 0003, Decisão 5.2).
 */
public class ConsultaFalsa extends ConsultaGrpc.ConsultaImplBase {

    public static final Metadata.Key<String> AUTORIZACAO =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    public final List<String> tokensRecebidos = new ArrayList<>();
    public final List<String> chamadas = new ArrayList<>();
    public io.grpc.Status falhaDoResumo;

    @Override
    public void resumoFundos(ResumoFundosRequest pedido, StreamObserver<ResumoFundosResponse> resposta) {
        chamadas.add("resumoFundos:" + pedido.getCondominioId());
        if (falhaDoResumo != null) {
            resposta.onError(falhaDoResumo.asRuntimeException());
            return;
        }
        resposta.onNext(ResumoFundosResponse.newBuilder()
                .setTemDados(true)
                .setArquivoId("arq-1")
                .setArquivoNome("fluxo-setembro.pdf")
                .setPeriodoInicio("2026-09-01")
                .setPeriodoFim("2026-09-30")
                .setSaldoAnterior("1000000.07")
                .setEntradas("250000.03")
                .setSaidas("125000.01")
                .setSaldoAtual("1125000.09")
                .setConferenciasComFalha(1)
                .addFundos(FundoNoPeriodo.newBuilder().setFundo("ORDINARIO").setSaldoAnterior("0.01")
                        .setEntradas("0.02").setSaidas("0.00").setSaldoAtual("0.03"))
                .build());
        resposta.onCompleted();
    }

    @Override
    public void listarLancamentos(ListarLancamentosRequest pedido, StreamObserver<Lancamento> resposta) {
        chamadas.add("listarLancamentos:" + pedido.getDataInicio() + ".." + pedido.getDataFim());
        resposta.onNext(Lancamento.newBuilder().setData("2026-09-03").setFundo("ORDINARIO")
                .setHistorico("Energia elétrica").setFornecedor("Enel").setDebito("1234.56").setCredito("0.00")
                .build());
        resposta.onNext(Lancamento.newBuilder().setData("2026-09-10").setFundo("ORDINARIO")
                .setHistorico("Água").setDebito("0.45").setCredito("0.00").build());
        resposta.onNext(Lancamento.newBuilder().setData("2026-09-20").setFundo("ORDINARIO")
                .setHistorico("Taxa condominial").setDebito("0.00").setCredito("500.00").build());
        resposta.onCompleted();
    }

    @Override
    public void listarArquivos(ListarArquivosRequest pedido, StreamObserver<ListarArquivosResponse> resposta) {
        chamadas.add("listarArquivos:" + pedido.getCategoria());
        resposta.onNext(ListarArquivosResponse.newBuilder()
                .addArquivos(ArquivoResumo.newBuilder().setId("arq-1").setCategoria("BALANCETE")
                        .setNome("fluxo-setembro.pdf").setStatus("CONCLUIDO").setPeriodoInicio("2026-09-01")
                        .setPeriodoFim("2026-09-30"))
                .build());
        resposta.onCompleted();
    }

    @Override
    public void conferenciasDoArquivo(ConferenciasDoArquivoRequest pedido,
            StreamObserver<ConferenciasDoArquivoResponse> resposta) {
        chamadas.add("conferenciasDoArquivo:" + pedido.getArquivoId());
        resposta.onNext(ConferenciasDoArquivoResponse.newBuilder()
                .addConferencias(Conferencia.newBuilder().setCodigo("F1").setDescricao("Soma das entradas")
                        .setOk(true))
                .addConferencias(Conferencia.newBuilder().setCodigo("F2").setDescricao("Saldo final")
                        .setOk(false).setDetalhe("diferença de 0,02"))
                .build());
        resposta.onCompleted();
    }

    /** Guarda o token de cada chamada. */
    public ServerInterceptor registrandoToken() {
        return new ServerInterceptor() {
            @Override
            public <P, R> ServerCall.Listener<P> interceptCall(ServerCall<P, R> chamada, Metadata cabecalhos,
                    ServerCallHandler<P, R> proximo) {
                tokensRecebidos.add(cabecalhos.get(AUTORIZACAO));
                return proximo.startCall(chamada, cabecalhos);
            }
        };
    }
}
