package br.com.condominioauditoria.backend.processamento;

import br.com.condominioauditoria.backend.arquivo.ArquivoRepository;
import br.com.condominioauditoria.backend.arquivo.StatusArquivo;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Andamento informado pelo rag. Mensagem de uma leitura antiga (outro processamentoId) é ignorada: o usuário pode
 * ter pedido reprocessamento no meio do caminho.
 */
@Service
public class StatusArquivoService {

    private final ArquivoRepository arquivos;

    StatusArquivoService(ArquivoRepository arquivos) {
        this.arquivos = arquivos;
    }

    @Transactional
    public void processando(UUID arquivoId, UUID processamentoId) {
        arquivos.findById(arquivoId)
                .filter(a -> a.ehDoProcessamento(processamentoId))
                .filter(a -> a.getStatus() == StatusArquivo.PENDENTE)
                .ifPresent(a -> a.iniciarProcessamento());
    }

    @Transactional
    public void falhou(UUID arquivoId, UUID processamentoId, String motivo) {
        arquivos.findById(arquivoId)
                .filter(a -> a.ehDoProcessamento(processamentoId))
                .ifPresent(a -> a.falhar(motivo));
    }
}
