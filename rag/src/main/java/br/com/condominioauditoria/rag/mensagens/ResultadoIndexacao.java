package br.com.condominioauditoria.rag.mensagens;

import java.util.UUID;

/** Mensagem rag → backend (fila backend.indexacao). Contrato: contracts/mensagens/v1/resultado-indexacao.schema.json. */
public record ResultadoIndexacao(int versao, UUID indexacaoId, UUID arquivoId, UUID condominioId, Situacao situacao,
        String motivo, Integer paginas, Integer trechos, String modeloEmbeddings, String versaoIndexador) {

    public enum Situacao {
        INDEXANDO, INDEXADO, SEM_TEXTO, RETIRADO, ERRO
    }

    public static ResultadoIndexacao indexando(IndexarArquivo p) {
        return new ResultadoIndexacao(1, p.indexacaoId(), p.arquivoId(), p.condominioId(), Situacao.INDEXANDO, null,
                null, null, null, null);
    }

    public static ResultadoIndexacao indexado(IndexarArquivo p, int paginas, int trechos, String modelo,
            String versaoIndexador) {
        return indexado(p, paginas, trechos, modelo, versaoIndexador, null);
    }

    /** {@code aviso}: ex.: indexado só para a busca por palavra porque o Ollama estava fora (vai em motivo). */
    public static ResultadoIndexacao indexado(IndexarArquivo p, int paginas, int trechos, String modelo,
            String versaoIndexador, String aviso) {
        return new ResultadoIndexacao(1, p.indexacaoId(), p.arquivoId(), p.condominioId(), Situacao.INDEXADO, aviso,
                paginas, trechos, modelo, versaoIndexador);
    }

    public static ResultadoIndexacao semTexto(IndexarArquivo p, String motivo, int paginas, String versaoIndexador) {
        return new ResultadoIndexacao(1, p.indexacaoId(), p.arquivoId(), p.condominioId(), Situacao.SEM_TEXTO, motivo,
                paginas, 0, null, versaoIndexador);
    }

    public static ResultadoIndexacao retirado(IndexarArquivo p) {
        return new ResultadoIndexacao(1, p.indexacaoId(), p.arquivoId(), p.condominioId(), Situacao.RETIRADO, null,
                null, null, null, null);
    }

    public static ResultadoIndexacao erro(IndexarArquivo p, String motivo) {
        return new ResultadoIndexacao(1, p.indexacaoId(), p.arquivoId(), p.condominioId(), Situacao.ERRO, motivo,
                null, null, null, null);
    }
}
