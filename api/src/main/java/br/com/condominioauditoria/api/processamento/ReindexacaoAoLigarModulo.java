package br.com.condominioauditoria.api.processamento;

import br.com.condominioauditoria.api.arquivo.Arquivo;
import br.com.condominioauditoria.api.arquivo.ArquivoRepository;
import br.com.condominioauditoria.api.mensagens.PublicadorIndexacao.ArquivosParaIndexar;
import br.com.condominioauditoria.api.modulo.ModuloAlterado;
import br.com.condominioauditoria.api.modulo.Modulos;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ligar o Assistente indexa o que já existe (RF-10.4): todos os arquivos do condomínio ganham pedido novo (Na fila,
 * visível na tela Arquivos) na mesma transação da alteração, e as mensagens saem em lote depois do commit, fora da
 * requisição. O rag pula o que já está indexado com o mesmo hash e modelo (RF-10.5, Q14).
 *
 * Desligar não faz nada aqui: o índice e os originais ficam guardados e nenhuma mensagem RETIRAR é publicada.
 */
@Component
class ReindexacaoAoLigarModulo {

    private final ArquivoRepository arquivos;
    private final ApplicationEventPublisher eventos;

    ReindexacaoAoLigarModulo(ArquivoRepository arquivos, ApplicationEventPublisher eventos) {
        this.arquivos = arquivos;
        this.eventos = eventos;
    }

    @EventListener
    void aoAlterar(ModuloAlterado alteracao) {
        if (!alteracao.ligado() || !Modulos.ASSISTENTE.equals(alteracao.modulo())) {
            return;
        }
        List<Arquivo> doCondominio = arquivos.findByCondominioIdOrderByEnviadoEmDesc(alteracao.condominioId());
        if (doCondominio.isEmpty()) {
            return;
        }
        doCondominio.forEach(Arquivo::novaIndexacao);
        List<UUID> ids = doCondominio.stream().map(Arquivo::getId).toList();
        eventos.publishEvent(new ArquivosParaIndexar(alteracao.condominioId(), ids));
    }
}
