package br.com.condominioauditoria.logging;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.util.function.Supplier;

/**
 * Request id in gRPC (ADR 0008; {@code contracts/correlacao.md}): metadata {@code x-request-id}. The client sends the
 * id of the current thread (or a new one); the server takes the received id (or creates one) and keeps it in the MDC on
 * every step of the call, because gRPC runs each step on a pool thread. Register the server interceptor as the
 * outermost one, so a call refused by the authentication is logged with its id.
 */
public final class RequestIdGrpc {

    public static final Metadata.Key<String> KEY = Metadata.Key.of(LogContext.GRPC_KEY,
            Metadata.ASCII_STRING_MARSHALLER);

    private RequestIdGrpc() {
    }

    /** Client side: attaches the request id of the current thread. */
    public static ClientInterceptor client() {
        return new ClientInterceptor() {
            @Override
            public <Q, R> ClientCall<Q, R> interceptCall(MethodDescriptor<Q, R> method, CallOptions options,
                    Channel next) {
                String requestId = LogContext.currentOrNewRequestId();
                return new SimpleForwardingClientCall<>(next.newCall(method, options)) {
                    @Override
                    public void start(Listener<R> listener, Metadata headers) {
                        headers.put(KEY, requestId);
                        super.start(listener, headers);
                    }
                };
            }
        };
    }

    /** Server side: the received (or a new) request id in the MDC during the whole call. */
    public static ServerInterceptor server() {
        return new ServerInterceptor() {
            @Override
            public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
                    ServerCallHandler<Q, R> next) {
                String requestId = LogContext.acceptOrCreate(headers.get(KEY));
                ServerCall.Listener<Q> delegate = inContext(requestId, () -> next.startCall(call, headers));
                return new SimpleForwardingServerCallListener<>(delegate) {
                    @Override
                    public void onMessage(Q message) {
                        inContext(requestId, () -> {
                            super.onMessage(message);
                            return null;
                        });
                    }

                    @Override
                    public void onHalfClose() {
                        inContext(requestId, () -> {
                            super.onHalfClose();
                            return null;
                        });
                    }

                    @Override
                    public void onCancel() {
                        inContext(requestId, () -> {
                            super.onCancel();
                            return null;
                        });
                    }

                    @Override
                    public void onComplete() {
                        inContext(requestId, () -> {
                            super.onComplete();
                            return null;
                        });
                    }

                    @Override
                    public void onReady() {
                        inContext(requestId, () -> {
                            super.onReady();
                            return null;
                        });
                    }
                };
            }
        };
    }

    private static <T> T inContext(String requestId, Supplier<T> action) {
        try (LogContext.Scope ignored = LogContext.with(LogContext.REQUEST_ID, requestId)) {
            return action.get();
        }
    }
}
