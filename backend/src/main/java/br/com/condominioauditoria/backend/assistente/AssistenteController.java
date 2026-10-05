package br.com.condominioauditoria.backend.assistente;

import br.com.condominioauditoria.backend.assistente.DtosAssistente.PedidoBuscaDocumentos;
import br.com.condominioauditoria.backend.assistente.DtosAssistente.PedidoPergunta;
import br.com.condominioauditoria.backend.assistente.DtosAssistente.RespostaAssistente;
import br.com.condominioauditoria.backend.assistente.DtosAssistente.TrechoDocumento;
import br.com.condominioauditoria.backend.condominio.CondominioRepository;
import br.com.condominioauditoria.backend.seguranca.AcessoCondominio;
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
    private final AcessoCondominio acesso;
    private final CondominioRepository condominios;

    AssistenteController(PerguntaAssistente pergunta, BuscaAssistente busca, AcessoCondominio acesso,
            CondominioRepository condominios) {
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
        acesso.exigir(condominioId);
        if (!condominios.existsById(condominioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado");
        }
    }
}
