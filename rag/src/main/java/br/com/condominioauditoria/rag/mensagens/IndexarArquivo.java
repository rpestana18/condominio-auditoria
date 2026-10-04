package br.com.condominioauditoria.rag.mensagens;

import java.time.LocalDate;
import java.util.UUID;

/** Mensagem backend → rag (fila rag.indexacao). Contrato: contracts/mensagens/v1/indexar-arquivo.schema.json. */
public record IndexarArquivo(int versao, Operacao operacao, UUID indexacaoId, UUID arquivoId, UUID condominioId,
        String categoria, String nomeOriginal, String caminho, String sha256, LocalDate competenciaInicio,
        LocalDate competenciaFim, Integer versaoArquivo, ModoEmbeddings modoEmbeddings, String modeloEmbeddings) {

    public enum Operacao {
        INDEXAR, RETIRAR
    }

    /** Ausente ou nulo = LOCAL. DESLIGADO = só trechos para a busca por palavra, sem vetores. */
    public enum ModoEmbeddings {
        LOCAL, DESLIGADO
    }

    public boolean comVetores() {
        return modoEmbeddings != ModoEmbeddings.DESLIGADO;
    }
}
