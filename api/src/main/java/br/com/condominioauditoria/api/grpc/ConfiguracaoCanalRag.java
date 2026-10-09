package br.com.condominioauditoria.api.grpc;

import br.com.condominioauditoria.api.config.PropriedadesCondominio;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Canal gRPC até o rag, aberto uma vez e reaproveitado (o gRPC multiplexa as chamadas). A conexão só é feita na
 * primeira chamada: o backend sobe mesmo com o rag fora do ar. Sem TLS na rede interna, como o servidor do backend.
 */
@Configuration
class ConfiguracaoCanalRag {

    @Bean(destroyMethod = "shutdown")
    ManagedChannel canalRag(PropriedadesCondominio propriedades) {
        return Grpc.newChannelBuilder(propriedades.rag().grpc(), InsecureChannelCredentials.create()).build();
    }
}
