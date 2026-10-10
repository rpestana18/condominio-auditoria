package br.com.condominioauditoria.rag.service;

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
 * Fake gRPC server of the api's query contract, with exact values in cents. It also keeps the token received, so the
 * test checks that the rag passes on the SAME token as the request (ADR 0003, Decision 5.2).
 */
public class FakeQuery extends ConsultaGrpc.ConsultaImplBase {

    public static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    public final List<String> receivedTokens = new ArrayList<>();
    public final List<String> calls = new ArrayList<>();
    public io.grpc.Status summaryFailure;

    @Override
    public void resumoFundos(ResumoFundosRequest request, StreamObserver<ResumoFundosResponse> response) {
        calls.add("resumoFundos:" + request.getCondominioId());
        if (summaryFailure != null) {
            response.onError(summaryFailure.asRuntimeException());
            return;
        }
        response.onNext(ResumoFundosResponse.newBuilder()
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
        response.onCompleted();
    }

    @Override
    public void listarLancamentos(ListarLancamentosRequest request, StreamObserver<Lancamento> response) {
        calls.add("listarLancamentos:" + request.getDataInicio() + ".." + request.getDataFim());
        response.onNext(Lancamento.newBuilder().setData("2026-09-03").setFundo("ORDINARIO")
                .setHistorico("Energia elétrica").setFornecedor("Enel").setDebito("1234.56").setCredito("0.00")
                .build());
        response.onNext(Lancamento.newBuilder().setData("2026-09-10").setFundo("ORDINARIO")
                .setHistorico("Água").setDebito("0.45").setCredito("0.00").build());
        response.onNext(Lancamento.newBuilder().setData("2026-09-20").setFundo("ORDINARIO")
                .setHistorico("Taxa condominial").setDebito("0.00").setCredito("500.00").build());
        response.onCompleted();
    }

    @Override
    public void listarArquivos(ListarArquivosRequest request, StreamObserver<ListarArquivosResponse> response) {
        calls.add("listarArquivos:" + request.getCategoria());
        response.onNext(ListarArquivosResponse.newBuilder()
                .addArquivos(ArquivoResumo.newBuilder().setId("arq-1").setCategoria("BALANCETE")
                        .setNome("fluxo-setembro.pdf").setStatus("CONCLUIDO").setPeriodoInicio("2026-09-01")
                        .setPeriodoFim("2026-09-30"))
                .build());
        response.onCompleted();
    }

    @Override
    public void conferenciasDoArquivo(ConferenciasDoArquivoRequest request,
            StreamObserver<ConferenciasDoArquivoResponse> response) {
        calls.add("conferenciasDoArquivo:" + request.getArquivoId());
        response.onNext(ConferenciasDoArquivoResponse.newBuilder()
                .addConferencias(Conferencia.newBuilder().setCodigo("F1").setDescricao("Soma das entradas")
                        .setOk(true))
                .addConferencias(Conferencia.newBuilder().setCodigo("F2").setDescricao("Saldo final")
                        .setOk(false).setDetalhe("diferença de 0,02"))
                .build());
        response.onCompleted();
    }

    /** Keeps the token of each call. */
    public ServerInterceptor recordingToken() {
        return new ServerInterceptor() {
            @Override
            public <P, R> ServerCall.Listener<P> interceptCall(ServerCall<P, R> call, Metadata headers,
                    ServerCallHandler<P, R> next) {
                receivedTokens.add(headers.get(AUTHORIZATION));
                return next.startCall(call, headers);
            }
        };
    }
}
