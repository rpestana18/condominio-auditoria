package br.com.condominioauditoria.app.processamento;

import br.com.condominioauditoria.app.arquivo.ArquivoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mudanças de status em transação própria (REQUIRES_NEW): a tela enxerga "Processando" na hora, e um "Falhou"
 * fica gravado mesmo quando a gravação dos dados sofre rollback.
 */
@Service
class StatusArquivoService {

    private final ArquivoRepository arquivos;

    StatusArquivoService(ArquivoRepository arquivos) {
        this.arquivos = arquivos;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processando(UUID arquivoId) {
        arquivos.findById(arquivoId).ifPresent(a -> a.iniciarProcessamento());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void falhou(UUID arquivoId, String motivo) {
        arquivos.findById(arquivoId).ifPresent(a -> a.falhar(motivo));
    }
}
