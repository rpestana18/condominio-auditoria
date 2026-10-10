package br.com.condominioauditoria.rag.service;

import br.com.condominioauditoria.contracts.query.v2.FileSummary;
import br.com.condominioauditoria.contracts.query.v2.FileCheck;
import br.com.condominioauditoria.contracts.query.v2.FileChecksRequest;
import br.com.condominioauditoria.contracts.query.v2.FileChecksResponse;
import br.com.condominioauditoria.contracts.query.v2.QueryGrpc;
import br.com.condominioauditoria.contracts.query.v2.FundInPeriod;
import br.com.condominioauditoria.contracts.query.v2.Entry;
import br.com.condominioauditoria.contracts.query.v2.ListFilesRequest;
import br.com.condominioauditoria.contracts.query.v2.ListFilesResponse;
import br.com.condominioauditoria.contracts.query.v2.ListEntriesRequest;
import br.com.condominioauditoria.contracts.query.v2.FundSummaryRequest;
import br.com.condominioauditoria.contracts.query.v2.FundSummaryResponse;
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
public class FakeQuery extends QueryGrpc.QueryImplBase {

    public static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    public final List<String> receivedTokens = new ArrayList<>();
    public final List<String> calls = new ArrayList<>();
    public io.grpc.Status summaryFailure;

    @Override
    public void fundSummary(FundSummaryRequest request, StreamObserver<FundSummaryResponse> response) {
        calls.add("resumoFundos:" + request.getCondominiumId());
        if (summaryFailure != null) {
            response.onError(summaryFailure.asRuntimeException());
            return;
        }
        response.onNext(FundSummaryResponse.newBuilder()
                .setHasData(true)
                .setFileId("arq-1")
                .setFileName("fluxo-setembro.pdf")
                .setPeriodStart("2026-09-01")
                .setPeriodEnd("2026-09-30")
                .setOpeningBalance("1000000.07")
                .setInflows("250000.03")
                .setOutflows("125000.01")
                .setClosingBalance("1125000.09")
                .setFailedChecks(1)
                .addFunds(FundInPeriod.newBuilder().setFund("ORDINARIO").setOpeningBalance("0.01")
                        .setInflows("0.02").setOutflows("0.00").setClosingBalance("0.03"))
                .build());
        response.onCompleted();
    }

    @Override
    public void listEntries(ListEntriesRequest request, StreamObserver<Entry> response) {
        calls.add("listarLancamentos:" + request.getDateFrom() + ".." + request.getDateTo());
        response.onNext(Entry.newBuilder().setDate("2026-09-03").setFund("ORDINARIO")
                .setMemo("Energia elétrica").setSupplier("Enel").setDebit("1234.56").setCredit("0.00")
                .build());
        response.onNext(Entry.newBuilder().setDate("2026-09-10").setFund("ORDINARIO")
                .setMemo("Água").setDebit("0.45").setCredit("0.00").build());
        response.onNext(Entry.newBuilder().setDate("2026-09-20").setFund("ORDINARIO")
                .setMemo("Taxa condominial").setDebit("0.00").setCredit("500.00").build());
        response.onCompleted();
    }

    @Override
    public void listFiles(ListFilesRequest request, StreamObserver<ListFilesResponse> response) {
        calls.add("listarArquivos:" + request.getCategory());
        response.onNext(ListFilesResponse.newBuilder()
                .addFiles(FileSummary.newBuilder().setId("arq-1").setCategory("TRIAL_BALANCE")
                        .setName("fluxo-setembro.pdf").setStatus("COMPLETED").setPeriodStart("2026-09-01")
                        .setPeriodEnd("2026-09-30"))
                .build());
        response.onCompleted();
    }

    @Override
    public void fileChecks(FileChecksRequest request,
            StreamObserver<FileChecksResponse> response) {
        calls.add("conferenciasDoArquivo:" + request.getFileId());
        response.onNext(FileChecksResponse.newBuilder()
                .addChecks(FileCheck.newBuilder().setCode("F1").setDescription("Soma das entradas")
                        .setOk(true))
                .addChecks(FileCheck.newBuilder().setCode("F2").setDescription("Saldo final")
                        .setOk(false).setDetail("diferença de 0,02"))
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
