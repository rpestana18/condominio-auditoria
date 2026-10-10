package br.com.condominioauditoria.api.grpc.server;

import br.com.condominioauditoria.api.exception.FeatureNotEnabledException;
import br.com.condominioauditoria.api.service.query.QueryService;
import br.com.condominioauditoria.contracts.query.v2.SearchDocumentsRequest;
import br.com.condominioauditoria.contracts.query.v2.SearchDocumentsResponse;
import br.com.condominioauditoria.contracts.query.v2.FileChecksRequest;
import br.com.condominioauditoria.contracts.query.v2.FileChecksResponse;
import br.com.condominioauditoria.contracts.query.v2.QueryGrpc;
import br.com.condominioauditoria.contracts.query.v2.Entry;
import br.com.condominioauditoria.contracts.query.v2.ListFilesRequest;
import br.com.condominioauditoria.contracts.query.v2.ListFilesResponse;
import br.com.condominioauditoria.contracts.query.v2.ListCondominiumsRequest;
import br.com.condominioauditoria.contracts.query.v2.ListCondominiumsResponse;
import br.com.condominioauditoria.contracts.query.v2.ListEntriesRequest;
import br.com.condominioauditoria.contracts.query.v2.FundSummaryRequest;
import br.com.condominioauditoria.contracts.query.v2.FundSummaryResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Entry point of contracts/grpc/query/v2/query.proto. Read-only: each rpc calls {@link QueryService} and turns
 * its exceptions into the gRPC status the contract promises.
 */
@Component
public class QueryGrpcService extends QueryGrpc.QueryImplBase {

    private static final Logger log = LoggerFactory.getLogger(QueryGrpcService.class);

    private final QueryService query;

    public QueryGrpcService(QueryService query) {
        this.query = query;
    }

    @Override
    public void listCondominiums(ListCondominiumsRequest request,
            StreamObserver<ListCondominiumsResponse> response) {
        respond(response, query::condominiums);
    }

    @Override
    public void fundSummary(FundSummaryRequest request, StreamObserver<FundSummaryResponse> response) {
        respond(response, () -> query.fundSummary(request));
    }

    @Override
    public void listFiles(ListFilesRequest request, StreamObserver<ListFilesResponse> response) {
        respond(response, () -> query.files(request));
    }

    @Override
    public void fileChecks(FileChecksRequest request,
            StreamObserver<FileChecksResponse> response) {
        respond(response, () -> query.fileChecks(request));
    }

    /** Streamed response: each entry goes out as soon as it is converted. */
    @Override
    public void listEntries(ListEntriesRequest request, StreamObserver<Entry> response) {
        try {
            query.ledgerEntries(request).forEach(response::onNext);
            response.onCompleted();
        } catch (RuntimeException error) {
            response.onError(translate(error));
        }
    }

    @Override
    public void searchDocuments(SearchDocumentsRequest request, StreamObserver<SearchDocumentsResponse> response) {
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
