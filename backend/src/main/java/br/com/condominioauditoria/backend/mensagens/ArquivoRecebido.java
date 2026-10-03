package br.com.condominioauditoria.backend.mensagens;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import java.util.UUID;

/** Mensagem backend → rag. Contrato: contracts/mensagens/v1/arquivo-recebido.schema.json. */
public record ArquivoRecebido(int versao, UUID processamentoId, UUID arquivoId, UUID condominioId, String categoria,
        String nomeOriginal, String caminho, String sha256) {

    static ArquivoRecebido de(Arquivo a) {
        return new ArquivoRecebido(1, a.getProcessamentoId(), a.getId(), a.getCondominioId(), a.getCategoria().name(),
                a.getNomeOriginal(), a.getCaminho(), a.getSha256());
    }
}
