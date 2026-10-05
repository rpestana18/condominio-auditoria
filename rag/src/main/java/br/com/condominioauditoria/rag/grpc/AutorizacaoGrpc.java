package br.com.condominioauditoria.rag.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * Guarda o metadado {@code authorization} do pedido no contexto da chamada, para o {@code Perguntar} exigir o token e
 * repassá-lo às ferramentas numéricas do backend (ADR 0003, Decisão 5.2: "com o token do próprio usuário").
 *
 * Conferir a assinatura do JWT continua fora de escopo (TODO da entrega 1, em {@link ServidorGrpc}): aqui só se
 * exige que o token exista e ele é repassado como veio. Quem verifica perfil, condomínio e módulo é o backend, antes
 * de chamar, e de novo na volta de cada ferramenta.
 */
final class AutorizacaoGrpc implements ServerInterceptor {

    static final Metadata.Key<String> CHAVE =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    static final Context.Key<String> AUTORIZACAO = Context.key("authorization");

    @Override
    public <P, R> ServerCall.Listener<P> interceptCall(ServerCall<P, R> chamada, Metadata cabecalhos,
            ServerCallHandler<P, R> proximo) {
        String token = cabecalhos.get(CHAVE);
        if (token == null || token.isBlank()) {
            return Contexts.interceptCall(Context.current(), chamada, cabecalhos, proximo);
        }
        return Contexts.interceptCall(Context.current().withValue(AUTORIZACAO, token), chamada, cabecalhos, proximo);
    }
}
