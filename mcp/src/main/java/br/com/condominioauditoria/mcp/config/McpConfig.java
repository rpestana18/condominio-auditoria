package br.com.condominioauditoria.mcp.config;

import br.com.condominioauditoria.mcp.config.properties.McpProperties;
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

    /**
     * Key, in the context of each MCP call, of the received Authorization header (already validated by Spring
     * Security).
     */
    public static final String AUTHORIZATION = "authorization";

    /**
     * Stateless MCP transport (each request is an independent POST), the same as Spring AI auto-configuration's, plus
     * one thing: it keeps the Authorization header in the call context. The tools run on another thread, so this is
     * how the user's token reaches the gRPC call.
     */
    @Bean
    WebMvcStatelessServerTransport webMvcStatelessServerTransport(@Qualifier("mcpServerJsonMapper") JsonMapper json,
            McpServerStreamableHttpProperties properties) {
        return WebMvcStatelessServerTransport.builder()
                .jsonMapper(new JacksonMcpJsonMapper(json))
                .messageEndpoint(properties.getMcpEndpoint())
                .contextExtractor(request -> {
                    String value = request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
                    return value == null ? McpTransportContext.EMPTY : McpTransportContext.create(Map.of(AUTHORIZATION,
                            value));
                })
                .build();
    }

    /** gRPC channel to the api, opened once and reused (gRPC multiplexes the calls). */
    @Bean(destroyMethod = "shutdown")
    ManagedChannel apiChannel(McpProperties properties) {
        return Grpc.newChannelBuilder(properties.api().address(), InsecureChannelCredentials.create()).build();
    }
}
