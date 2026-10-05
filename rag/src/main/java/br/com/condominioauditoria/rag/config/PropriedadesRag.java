package br.com.condominioauditoria.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Parâmetros do serviço rag (bloco "rag" do application.yml). */
@ConfigurationProperties(prefix = "rag")
public record PropriedadesRag(Armazenamento armazenamento, Leitor leitor, Grpc grpc, Indexacao indexacao,
        Embeddings embeddings, Assistente assistente) {

    /** Mesma pasta (ou bucket) onde o backend guarda os originais; o rag só lê. */
    public record Armazenamento(String tipo, String pasta) {
    }

    public record Leitor(String url, int timeoutSegundos) {
    }

    /** Servidor gRPC do assistente (contracts/grpc/assistente/v1), só na rede interna. */
    public record Grpc(int porta) {
    }

    public record Indexacao(int paralelismo) {
    }

    /** Chamadas ao Ollama (embeddings locais, ADR 0003, Decisão 2). */
    public record Embeddings(int timeoutConexaoSegundos, int timeoutSegundos, int lote) {
    }

    /**
     * Chat do assistente (ADR 0003, Decisões 1 e 5.2). Nada aqui é configuração de condomínio: o modo, o provedor, o
     * modelo e a chave cifrada vêm em cada pedido Perguntar, resolvidos pelo backend.
     *
     * @param chavePrivadaArquivo arquivo PEM PKCS#8 da chave privada do rag (RAG_CHAVE_PRIVADA_ARQUIVO)
     * @param gerarChaveDev gera o par RSA 3072 quando o arquivo não existe (só desenvolvimento)
     * @param anthropicUrl endereço da API do Claude (RAG_ANTHROPIC_URL)
     * @param prazoProvedorSegundos prazo de cada chamada ao provedor
     * @param tentativasProvedor novas tentativas do cliente HTTP em erro de rede ou 5xx
     * @param maxTokens teto de tokens de saída por volta (sem streaming)
     * @param esforco output_config.effort (low, medium, high, xhigh, max); vazio = não enviar
     * @param voltasFerramentas teto de voltas do laço de ferramentas numa tentativa
     * @param historicoMaximo quantas trocas anteriores da conversa o rag usa
     * @param backendGrpc endereço do gRPC Consulta do backend (BACKEND_GRPC)
     * @param prazoFerramentaSegundos prazo de cada chamada de ferramenta no backend
     * @param termosConduta palavras que o modelo não pode usar fora de citação literal (RF-04.15, Q11)
     */
    public record Assistente(String chavePrivadaArquivo, boolean gerarChaveDev, String anthropicUrl,
            int prazoProvedorSegundos, int tentativasProvedor, int maxTokens, String esforco, int voltasFerramentas,
            int historicoMaximo, String backendGrpc, int prazoFerramentaSegundos, java.util.List<String> termosConduta) {
    }
}
