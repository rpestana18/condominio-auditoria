package br.com.condominioauditoria.rag.mensagens;

import java.util.UUID;

/** Mensagem backend → rag. Contrato: contracts/mensagens/v1/arquivo-recebido.schema.json. */
public record ArquivoRecebido(int versao, UUID processamentoId, UUID arquivoId, UUID condominioId, String categoria,
        String nomeOriginal, String caminho, String sha256) {
}
