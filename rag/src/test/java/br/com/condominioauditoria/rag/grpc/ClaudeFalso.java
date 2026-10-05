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
 * API de mensagens do Claude falsa, no servidor HTTP do próprio JDK. Responde na ordem o que o teste enfileirar e
 * guarda os pedidos recebidos (para conferir que vão o esquema de saída, as ferramentas e os trechos).
 *
 * Nenhum teste fala com o provedor de verdade.
 */
final class ClaudeFalso implements AutoCloseable {

    record Preparada(int status, String corpo) {
    }

    private final HttpServer servidor;
    private final Deque<Preparada> respostas = new ArrayDeque<>();
    final List<String> pedidos = new ArrayList<>();
    final List<String> chavesRecebidas = new ArrayList<>();

    ClaudeFalso() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1/messages", this::atender);
        servidor.start();
    }

    String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    ClaudeFalso responde(String corpo) {
        respostas.add(new Preparada(200, corpo));
        return this;
    }

    ClaudeFalso respondeStatus(int status, String corpo) {
        respostas.add(new Preparada(status, corpo));
        return this;
    }

    private void atender(HttpExchange troca) throws IOException {
        chavesRecebidas.add(troca.getRequestHeaders().getFirst("x-api-key"));
        pedidos.add(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        Preparada preparada = respostas.poll();
        if (preparada == null) {
            preparada = new Preparada(500, "{\"type\":\"error\",\"error\":{\"type\":\"api_error\","
                    + "\"message\":\"o teste não preparou mais nenhuma resposta\"}}");
        }
        byte[] corpo = preparada.corpo().getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().add("content-type", "application/json");
        troca.sendResponseHeaders(preparada.status(), corpo.length);
        try (var saida = troca.getResponseBody()) {
            saida.write(corpo);
        }
    }

    @Override
    public void close() {
        servidor.stop(0);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Corpos de resposta no formato da API de mensagens
    // ---------------------------------------------------------------------------------------------------------------

    /** Resposta final: um bloco de texto com o JSON do esquema. */
    static String comTexto(String jsonDaResposta, int tokensEntrada, int tokensSaida) {
        return """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-sonnet-5-5",
                 "content":[{"type":"text","text":%s}],
                 "stop_reason":"end_turn","stop_sequence":null,
                 "usage":{"input_tokens":%d,"output_tokens":%d}}""".formatted(aspas(jsonDaResposta), tokensEntrada,
                tokensSaida);
    }

    /** Resposta pedindo uma ferramenta. */
    static String comFerramenta(String id, String nome, String argumentosJson, int tokensEntrada, int tokensSaida) {
        return """
                {"id":"msg_2","type":"message","role":"assistant","model":"claude-sonnet-5-5",
                 "content":[{"type":"tool_use","id":"%s","name":"%s","input":%s,"caller":{"type":"direct"}}],
                 "stop_reason":"tool_use","stop_sequence":null,
                 "usage":{"input_tokens":%d,"output_tokens":%d}}""".formatted(id, nome, argumentosJson,
                tokensEntrada, tokensSaida);
    }

    /** Recusa de segurança do modelo. */
    static String comRecusa() {
        return """
                {"id":"msg_3","type":"message","role":"assistant","model":"claude-sonnet-5-5",
                 "content":[],
                 "stop_reason":"refusal","stop_sequence":null,
                 "stop_details":{"type":"refusal","explanation":"pedido fora da política de uso"},
                 "usage":{"input_tokens":12,"output_tokens":0}}""";
    }

    static String erro(String tipo, String mensagem) {
        return "{\"type\":\"error\",\"error\":{\"type\":\"" + tipo + "\",\"message\":\"" + mensagem + "\"}}";
    }

    /** Texto JSON dentro de um campo JSON. */
    private static String aspas(String texto) {
        StringBuilder saida = new StringBuilder("\"");
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '"' -> saida.append("\\\"");
                case '\\' -> saida.append("\\\\");
                case '\n' -> saida.append("\\n");
                case '\r' -> saida.append("\\r");
                case '\t' -> saida.append("\\t");
                default -> saida.append(c);
            }
        }
        return saida.append('"').toString();
    }
}
