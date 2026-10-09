package br.com.condominioauditoria.api.mensagens;

import java.util.UUID;

/**
 * Mensagem rag → backend (fila backend.indexacao). Contrato: contracts/mensagens/v1/resultado-indexacao.schema.json.
 * Estes registros são do backend: o rag tem os dele. Só o contrato JSON é comum aos dois.
 */
public record ResultadoIndexacao(int versao, UUID indexacaoId, UUID arquivoId, UUID condominioId, Situacao situacao,
        String motivo, Integer paginas, Integer trechos, String modeloEmbeddings, String versaoIndexador) {

    public enum Situacao {
        INDEXANDO, INDEXADO, SEM_TEXTO, RETIRADO, ERRO
    }
}
