package br.com.condominioauditoria.rag.grpc;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Fake Claude messages API, on the JDK's own HTTP server. Answers in order what the test enqueued and keeps the
 * requests received (to check that the output schema, the tools and the chunks are sent).
 *
 * No test talks to the real provider.
 */
final class FakeClaude implements AutoCloseable {

    record Prepared(int status, String body) {
    }

    private final HttpServer server;
    private final Deque<Prepared> responses = new ArrayDeque<>();
    final List<String> requests = new ArrayList<>();
    final List<String> receivedKeys = new ArrayList<>();

    FakeClaude() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", this::handle);
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    FakeClaude respond(String body) {
        responses.add(new Prepared(200, body));
        return this;
    }

    FakeClaude respondStatus(int status, String body) {
        responses.add(new Prepared(status, body));
        return this;
    }

    private void handle(HttpExchange exchange) throws IOException {
        receivedKeys.add(exchange.getRequestHeaders().getFirst("x-api-key"));
        requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        Prepared prepared = responses.poll();
        if (prepared == null) {
            prepared = new Prepared(500, "{\"type\":\"error\",\"error\":{\"type\":\"api_error\","
                    + "\"message\":\"o teste não preparou mais nenhuma resposta\"}}");
        }
        byte[] body = prepared.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("content-type", "application/json");
        exchange.sendResponseHeaders(prepared.status(), body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Response bodies in the messages API format
    // ---------------------------------------------------------------------------------------------------------------

    /** Final answer: a text block with the schema JSON. */
    static String withText(String responseJson, int inputTokens, int outputTokens) {
        return """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-sonnet-5-5",
                 "content":[{"type":"text","text":%s}],
                 "stop_reason":"end_turn","stop_sequence":null,
                 "usage":{"input_tokens":%d,"output_tokens":%d}}""".formatted(quote(responseJson), inputTokens,
                outputTokens);
    }

    /** Answer requesting a tool. */
    static String withTool(String id, String name, String argumentsJson, int inputTokens, int outputTokens) {
        return """
                {"id":"msg_2","type":"message","role":"assistant","model":"claude-sonnet-5-5",
                 "content":[{"type":"tool_use","id":"%s","name":"%s","input":%s,"caller":{"type":"direct"}}],
                 "stop_reason":"tool_use","stop_sequence":null,
                 "usage":{"input_tokens":%d,"output_tokens":%d}}""".formatted(id, name, argumentsJson,
                inputTokens, outputTokens);
    }

    /** Model safety refusal. */
    static String withRefusal() {
        return """
                {"id":"msg_3","type":"message","role":"assistant","model":"claude-sonnet-5-5",
                 "content":[],
                 "stop_reason":"refusal","stop_sequence":null,
                 "stop_details":{"type":"refusal","explanation":"pedido fora da política de uso"},
                 "usage":{"input_tokens":12,"output_tokens":0}}""";
    }

    static String error(String type, String message) {
        return "{\"type\":\"error\",\"error\":{\"type\":\"" + type + "\",\"message\":\"" + message + "\"}}";
    }

    /** JSON text inside a JSON field. */
    private static String quote(String text) {
        StringBuilder output = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> output.append(c);
            }
        }
        return output.append('"').toString();
    }
}
