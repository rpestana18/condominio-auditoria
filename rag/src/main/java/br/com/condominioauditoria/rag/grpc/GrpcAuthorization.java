package br.com.condominioauditoria.rag.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * Keeps the request's {@code authorization} metadata in the call context, so {@code Perguntar} can require the token
 * and pass it on to the api's numeric tools (ADR 0003, Decision 5.2: "com o token do próprio usuário").
 *
 * Checking the JWT signature is still out of scope (TODO from delivery 1, in {@link GrpcServer}): here the token is
 * only required to exist and is passed on as it came. Role, condominium and module are checked by the api, before
 * calling, and again on the way back of each tool.
 */
final class GrpcAuthorization implements ServerInterceptor {

    static final Metadata.Key<String> KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    static final Context.Key<String> AUTHORIZATION = Context.key("authorization");

    @Override
    public <P, R> ServerCall.Listener<P> interceptCall(ServerCall<P, R> call, Metadata headers,
            ServerCallHandler<P, R> next) {
        String token = headers.get(KEY);
        if (token == null || token.isBlank()) {
            return Contexts.interceptCall(Context.current(), call, headers, next);
        }
        return Contexts.interceptCall(Context.current().withValue(AUTHORIZATION, token), call, headers, next);
    }
}
