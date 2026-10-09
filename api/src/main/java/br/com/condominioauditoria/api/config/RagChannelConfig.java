package br.com.condominioauditoria.api.config;

import br.com.condominioauditoria.api.config.properties.ApiProperties;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC channel to the rag, opened once and reused (gRPC multiplexes the calls). The connection is only made on the
 * first call: the api starts even with the rag down. No TLS on the internal network, like the api's own server.
 */
@Configuration
class RagChannelConfig {

    @Bean(destroyMethod = "shutdown")
    ManagedChannel ragChannel(ApiProperties properties) {
        return Grpc.newChannelBuilder(properties.rag().grpc(), InsecureChannelCredentials.create()).build();
    }
}
