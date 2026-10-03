package br.com.condominioauditoria.mcp.config;

import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import java.util.Map;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class McpConfig {

    /** Chave, no contexto de cada chamada MCP, do cabeçalho Authorization recebido (já validado pelo Spring Security). */
    public static final String AUTORIZACAO = "authorization";

    /**
     * Transporte MCP sem sessão (cada pedido é um POST independente), igual ao da autoconfiguração do Spring AI,
     * mais uma coisa: guarda o cabeçalho Authorization no contexto da chamada. As ferramentas rodam em outra thread,
     * então é por aqui que o token do usuário chega até a chamada gRPC.
     */
    @Bean
    WebMvcStatelessServerTransport webMvcStatelessServerTransport(@Qualifier("mcpServerJsonMapper") JsonMapper json,
            McpServerStreamableHttpProperties propriedades) {
        return WebMvcStatelessServerTransport.builder()
                .jsonMapper(new JacksonMcpJsonMapper(json))
                .messageEndpoint(propriedades.getMcpEndpoint())
                .contextExtractor(pedido -> {
                    String valor = pedido.headers().firstHeader(HttpHeaders.AUTHORIZATION);
                    return valor == null ? McpTransportContext.EMPTY : McpTransportContext.create(Map.of(AUTORIZACAO, valor));
                })
                .build();
    }

    /** Canal gRPC até o backend, aberto uma vez e reaproveitado (o gRPC multiplexa as chamadas). */
    @Bean(destroyMethod = "shutdown")
    ManagedChannel canalBackend(PropriedadesMcp propriedades) {
        return Grpc.newChannelBuilder(propriedades.backend().endereco(), InsecureChannelCredentials.create()).build();
    }
}
