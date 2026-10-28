package br.com.condominioauditoria.logging;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.CallOptions;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.ServerCalls;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** The request id goes from the caller's MDC to the server's MDC through the gRPC metadata (ADR 0008). */
class RequestIdGrpcTest {

    private static final MethodDescriptor.Marshaller<String> TEXT = new MethodDescriptor.Marshaller<>() {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    };
    private static final MethodDescriptor<String, String> ECHO = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY).setFullMethodName("test.Echo/Echo")
            .setRequestMarshaller(TEXT).setResponseMarshaller(TEXT).build();

    private final AtomicReference<String> seen = new AtomicReference<>();
    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void start() throws IOException {
        String name = InProcessServerBuilder.generateName();
        ServerServiceDefinition service = ServerServiceDefinition.builder("test.Echo")
                .addMethod(ECHO, ServerCalls.asyncUnaryCall((request, response) -> {
                    seen.set(MDC.get(LogContext.REQUEST_ID));
                    response.onNext(request);
                    response.onCompleted();
                })).build();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(ServerInterceptors.intercept(service, RequestIdGrpc.server())).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
        MDC.clear();
    }

    @Test
    void theCallerRequestIdReachesTheServerMdc() {
        try (LogContext.Scope ignored = LogContext.with(LogContext.REQUEST_ID, "req-123")) {
            ClientCalls.blockingUnaryCall(ClientInterceptors.intercept(channel, RequestIdGrpc.client()), ECHO,
                    CallOptions.DEFAULT, "oi");
        }
        assertThat(seen.get()).isEqualTo("req-123");
        assertThat(MDC.get(LogContext.REQUEST_ID)).isNull();
    }

    @Test
    void withoutARequestIdTheClientCreatesOne() {
        ClientCalls.blockingUnaryCall(ClientInterceptors.intercept(channel, RequestIdGrpc.client()), ECHO,
                CallOptions.DEFAULT, "oi");

        assertThat(LogContext.isValid(seen.get())).isTrue();
    }

    @Test
    void anInvalidReceivedIdIsReplacedSoNothingIsInjectedInTheLog() {
        Metadata headers = new Metadata();
        headers.put(RequestIdGrpc.KEY, "x y=1 forjado");
        ClientCalls.blockingUnaryCall(ClientInterceptors.intercept(channel,
                MetadataUtils.newAttachHeadersInterceptor(headers)), ECHO, CallOptions.DEFAULT, "oi");

        assertThat(seen.get()).isNotEqualTo("x y=1 forjado");
        assertThat(LogContext.isValid(seen.get())).isTrue();
    }

    @Test
    void scopeRestoresThePreviousValue() {
        MDC.put(LogContext.FILE_ID, "antes");
        try (LogContext.Scope ignored = LogContext.with(java.util.Map.of(LogContext.FILE_ID, "durante"))) {
            assertThat(MDC.get(LogContext.FILE_ID)).isEqualTo("durante");
        }
        assertThat(MDC.get(LogContext.FILE_ID)).isEqualTo("antes");
    }
}
