package br.com.condominioauditoria.backend.mensagens;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Mensagem backend → rag (fila rag.indexacao). Contrato: contracts/mensagens/v1/indexar-arquivo.schema.json.
 * O conteúdo do arquivo não vai na mensagem: o rag lê o original pelo caminho.
 *
 * Entrega 1: competência e versão do arquivo ainda não existem no backend (vão nulas); modo e modelo de embeddings
 * ficam de fora da mensagem quando nulos (= LOCAL com o modelo padrão do rag) até a configuração de IA por condomínio
 * (entrega 2).
 */
public record IndexarArquivo(int versao, Operacao operacao, UUID indexacaoId, UUID arquivoId, UUID condominioId,
        String categoria, String nomeOriginal, String caminho, String sha256, LocalDate competenciaInicio,
        LocalDate competenciaFim, Integer versaoArquivo,
        @JsonInclude(JsonInclude.Include.NON_NULL) String modoEmbeddings,
        @JsonInclude(JsonInclude.Include.NON_NULL) String modeloEmbeddings) {

    public enum Operacao {
        INDEXAR, RETIRAR
    }

    static IndexarArquivo indexar(Arquivo a) {
        return new IndexarArquivo(1, Operacao.INDEXAR, a.getIndexacaoId(), a.getId(), a.getCondominioId(),
                a.getCategoria().name(), a.getNomeOriginal(), a.getCaminho(), a.getSha256(), null, null, null, null,
                null);
    }
}
