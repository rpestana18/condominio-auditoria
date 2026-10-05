package br.com.condominioauditoria.rag.assistente.pergunta;

import br.com.condominioauditoria.rag.indice.BuscaDocumentos;
import br.com.condominioauditoria.rag.indice.RepositorioIndice;
import java.util.List;

/**
 * Uma pergunta do chat, já traduzida do contrato gRPC. Nada aqui é lido de banco pelo rag: o provedor, o modelo e a
 * chave cifrada vêm resolvidos pelo backend (ADR 0003, Decisão 4).
 *
 * @param filtros filtros da busca, aplicados antes da busca (RF-04.3, RF-04.10)
 * @param modoPedido PALAVRA quando os embeddings do assistente estão desligados
 * @param chaveCifrada envelope da chave de API do condomínio (só o rag decifra, na hora da chamada)
 * @param autorizacao o metadado "authorization" deste pedido, repassado às ferramentas numéricas
 */
public record PedidoPergunta(RepositorioIndice.FiltrosBusca filtros, String pergunta, List<Troca> historico,
        BuscaDocumentos.Modo modoPedido, String provedor, String modelo, byte[] chaveCifrada, int limiteTrechos,
        String autorizacao) {

    public record Troca(String pergunta, String resposta) {
    }

    public static final int MAXIMO_CARACTERES = 2000;
    /** Trechos oferecidos ao modelo: 0 = padrão 8, acima de 20 reduz a 20 (assistente.proto). */
    public static final int TRECHOS_PADRAO = 8;
    public static final int TRECHOS_MAXIMO = 20;
}
