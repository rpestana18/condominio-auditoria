package br.com.condominioauditoria.mcp.config;

import br.com.condominioauditoria.logging.LogContext;
import br.com.condominioauditoria.mcp.config.properties.McpProperties;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import java.util.HashMap;
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
     * Key, in the context of each MCP call, of the request id (ADR 0008): the X-Request-Id sent by the MCP client, or
     * a new one per call. It goes to the api in the gRPC metadata and to every log line of the tool.
     */
    public static final String REQUEST_ID = LogContext.REQUEST_ID;

    /**
     * Stateless MCP transport (each request is an independent POST), the same as Spring AI auto-configuration's, plus
     * one thing: it keeps the Authorization header in the call context. The tools run on another thread, so this is
     * how the user's token reaches the gRPC call. The request id travels the same way.
     */
    @Bean
    WebMvcStatelessServerTransport webMvcStatelessServerTransport(@Qualifier("mcpServerJsonMapper") JsonMapper json,
            McpServerStreamableHttpProperties properties) {
        return WebMvcStatelessServerTransport.builder()
                .jsonMapper(new JacksonMcpJsonMapper(json))
                .messageEndpoint(properties.getMcpEndpoint())
                .contextExtractor(request -> {
                    Map<String, Object> context = new HashMap<>();
                    context.put(REQUEST_ID, LogContext.acceptOrCreate(request.headers().firstHeader(
                            LogContext.HTTP_HEADER)));
                    String value = request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
                    if (value != null) {
                        context.put(AUTHORIZATION, value);
                    }
                    return McpTransportContext.create(context);
                })
                .build();
    }

    /** gRPC channel to the api, opened once and reused (gRPC multiplexes the calls). */
    @Bean(destroyMethod = "shutdown")
    ManagedChannel apiChannel(McpProperties properties) {
        return Grpc.newChannelBuilder(properties.api().address(), InsecureChannelCredentials.create()).build();
    }
}
