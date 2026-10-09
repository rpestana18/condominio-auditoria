package br.com.condominioauditoria.api.assistente;

import br.com.condominioauditoria.api.assistente.DtosAssistente.PedidoBuscaDocumentos;
import br.com.condominioauditoria.api.assistente.DtosAssistente.PedidoPergunta;
import br.com.condominioauditoria.api.assistente.DtosAssistente.RespostaAssistente;
import br.com.condominioauditoria.api.assistente.DtosAssistente.TrechoDocumento;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tela "Assistente" (RF-04.8 a 04.18). Pergunta e busca: USUARIO, GESTOR e ADMIN com acesso ao condomínio (RF-04.3),
 * sempre com o módulo Assistente ligado (403 "Módulo Assistente não contratado para este condomínio." sem ele).
 */
@RestController
@RequestMapping("/api/condominios/{condominioId}/assistente")
@PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
class AssistenteController {

    private final PerguntaAssistente pergunta;
    private final BuscaAssistente busca;
    private final CondominiumAccess acesso;
    private final CondominiumRepository condominios;

    AssistenteController(PerguntaAssistente pergunta, BuscaAssistente busca, CondominiumAccess acesso,
            CondominiumRepository condominios) {
        this.pergunta = pergunta;
        this.busca = busca;
        this.acesso = acesso;
        this.condominios = condominios;
    }

    @PostMapping("/perguntas")
    RespostaAssistente perguntar(@PathVariable UUID condominioId,
            @RequestBody(required = false) PedidoPergunta pedido) {
        exigirCondominio(condominioId);
        return pergunta.perguntar(condominioId, pedido);
    }

    @PostMapping("/busca")
    List<TrechoDocumento> buscar(@PathVariable UUID condominioId,
            @RequestBody(required = false) PedidoBuscaDocumentos pedido) {
        exigirCondominio(condominioId);
        return busca.buscar(condominioId, pedido);
    }

    private void exigirCondominio(UUID condominioId) {
        acesso.require(condominioId);
        if (!condominios.existsById(condominioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado");
        }
    }
}
