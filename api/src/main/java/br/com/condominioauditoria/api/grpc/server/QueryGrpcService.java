package br.com.condominioauditoria.api.grpc.server;

import br.com.condominioauditoria.api.exception.FeatureNotEnabledException;
import br.com.condominioauditoria.api.service.query.QueryService;
import br.com.condominioauditoria.contratos.consulta.v1.BuscarDocumentosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.BuscarDocumentosResponse;
import br.com.condominioauditoria.contratos.consulta.v1.ConferenciasDoArquivoRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ConferenciasDoArquivoResponse;
import br.com.condominioauditoria.contratos.consulta.v1.ConsultaGrpc;
import br.com.condominioauditoria.contratos.consulta.v1.Lancamento;
import br.com.condominioauditoria.contratos.consulta.v1.ListarArquivosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ListarArquivosResponse;
import br.com.condominioauditoria.contratos.consulta.v1.ListarCondominiosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ListarCondominiosResponse;
import br.com.condominioauditoria.contratos.consulta.v1.ListarLancamentosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ResumoFundosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ResumoFundosResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Entry point of contracts/grpc/consulta/v1/consulta.proto. Read-only: each rpc calls {@link QueryService} and turns
 * its exceptions into the gRPC status the contract promises.
 */
@Component
public class QueryGrpcService extends ConsultaGrpc.ConsultaImplBase {

    private static final Logger log = LoggerFactory.getLogger(QueryGrpcService.class);

    private final QueryService query;

    public QueryGrpcService(QueryService query) {
        this.query = query;
    }

    @Override
    public void listarCondominios(ListarCondominiosRequest request,
            StreamObserver<ListarCondominiosResponse> response) {
        respond(response, query::condominiums);
    }

    @Override
    public void resumoFundos(ResumoFundosRequest request, StreamObserver<ResumoFundosResponse> response) {
        respond(response, () -> query.fundSummary(request));
    }

    @Override
    public void listarArquivos(ListarArquivosRequest request, StreamObserver<ListarArquivosResponse> response) {
        respond(response, () -> query.files(request));
    }

    @Override
    public void conferenciasDoArquivo(ConferenciasDoArquivoRequest request,
            StreamObserver<ConferenciasDoArquivoResponse> response) {
        respond(response, () -> query.fileChecks(request));
    }

    /** Streamed response: each entry goes out as soon as it is converted. */
    @Override
    public void listarLancamentos(ListarLancamentosRequest request, StreamObserver<Lancamento> response) {
        try {
            query.ledgerEntries(request).forEach(response::onNext);
            response.onCompleted();
        } catch (RuntimeException error) {
            response.onError(translate(error));
        }
    }

    @Override
    public void buscarDocumentos(BuscarDocumentosRequest request, StreamObserver<BuscarDocumentosResponse> response) {
        respond(response, () -> query.searchDocuments(request));
    }

    private static <T> void respond(StreamObserver<T> response, Supplier<T> action) {
        try {
            response.onNext(action.get());
            response.onCompleted();
        } catch (RuntimeException error) {
            response.onError(translate(error));
        }
    }

    private static StatusRuntimeException translate(RuntimeException error) {
        return switch (error) {
            case StatusRuntimeException s -> s;
            case AccessDeniedException a ->
                    Status.PERMISSION_DENIED.withDescription(a.getMessage()).asRuntimeException();
            case FeatureNotEnabledException m ->
                    Status.FAILED_PRECONDITION.withDescription(m.getMessage()).asRuntimeException();
            case IllegalArgumentException i ->
                    Status.INVALID_ARGUMENT.withDescription(i.getMessage()).asRuntimeException();
            default -> {
                log.error("Erro no gRPC de consulta", error);
                yield Status.INTERNAL.withDescription("Erro interno no backend").asRuntimeException();
            }
        };
    }
}
