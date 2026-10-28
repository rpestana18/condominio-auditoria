package br.com.condominioauditoria.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.contracts.query.v2.CondominiumSummary;
import br.com.condominioauditoria.contracts.query.v2.ListCondominiumsRequest;
import br.com.condominioauditoria.contracts.query.v2.ListCondominiumsResponse;
import br.com.condominioauditoria.contracts.query.v2.QueryGrpc;
import br.com.condominioauditoria.logging.LogContext;
import br.com.condominioauditoria.logging.RequestIdGrpc;
import br.com.condominioauditoria.mcp.client.ApiClient;
import br.com.condominioauditoria.mcp.config.McpConfig;
import br.com.condominioauditoria.mcp.config.properties.McpProperties;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * ADR 0008: the request id of the MCP call (X-Request-Id of the MCP client, kept in the call context) reaches the api in
 * the gRPC metadata and is in the api's MDC; a refusal of the api is one WARN line in the mcp, which had no log.
 */
@ExtendWith(OutputCaptureExtension.class)
class RequestIdPropagationTest {

    private final AtomicReference<String> apiRequestId = new AtomicReference<>();
    private final AtomicReference<Status> error = new AtomicReference<>();
    private Server server;
    private ManagedChannel channel;
    private CondominiumTools tools;

    @BeforeEach
    void start() throws Exception {
        String name = InProcessServerBuilder.generateName();
        var fakeApi = new QueryGrpc.QueryImplBase() {
            @Override
            public void listCondominiums(ListCondominiumsRequest request,
                    StreamObserver<ListCondominiumsResponse> response) {
                apiRequestId.set(MDC.get(LogContext.REQUEST_ID));
                if (error.get() != null) {
                    response.onError(error.get().asRuntimeException());
                    return;
                }
                response.onNext(ListCondominiumsResponse.newBuilder().addCondominiums(CondominiumSummary.newBuilder()
                        .setId("6f1d2c1e-3b4a-4c8e-9a51-2815a0000001").setName("Mio")).build());
                response.onCompleted();
            }
        };
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(ServerInterceptors.intercept(fakeApi, RequestIdGrpc.server())).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        tools = new CondominiumTools(new ApiClient(channel, new McpProperties(new McpProperties.Api("em-memoria", 5))));
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    private static McpTransportContext context(String requestId) {
        return McpTransportContext.create(Map.of(McpConfig.AUTHORIZATION, "Bearer t", McpConfig.REQUEST_ID,
                requestId));
    }

    @Test
    void theRequestIdOfTheMcpCallReachesTheApi() {
        assertThat(tools.listCondominiums(context("req-mcp-1"))).hasSize(1);

        assertThat(apiRequestId.get()).isEqualTo("req-mcp-1");
        assertThat(MDC.get(LogContext.REQUEST_ID)).isNull();
    }

    @Test
    void aRefusalOfTheApiIsLoggedInTheMcp(CapturedOutput output) {
        error.set(Status.PERMISSION_DENIED.withDescription("Sem acesso a este condomínio"));

        assertThatThrownBy(() -> tools.listCondominiums(context("req-mcp-2")))
                .hasMessage("Sem acesso a este condomínio");
        assertThat(apiRequestId.get()).isEqualTo("req-mcp-2");
        assertThat(output.getOut()).contains("WARN").contains("PERMISSION_DENIED")
                .contains("Sem acesso a este condomínio").doesNotContain("Bearer");
    }
}
