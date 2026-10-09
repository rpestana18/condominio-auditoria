package br.com.condominioauditoria.api.controller.assistant;

import br.com.condominioauditoria.api.dto.request.assistant.DocumentSearchRequest;
import br.com.condominioauditoria.api.dto.request.assistant.QuestionRequest;
import br.com.condominioauditoria.api.dto.response.assistant.AssistantAnswerResponse;
import br.com.condominioauditoria.api.dto.response.assistant.DocumentChunkResponse;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.assistant.AssistantQuestionService;
import br.com.condominioauditoria.api.service.assistant.AssistantSearchService;
import br.com.condominioauditoria.api.service.condominium.CondominiumService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The "Assistente" screen (RF-04.8 to 04.18). Question and search: USUARIO, GESTOR and ADMIN with access to the
 * condominium (RF-04.3), always with the Assistant feature enabled (403 "Módulo Assistente não contratado para este
 * condomínio." without it).
 */
@RestController
@RequestMapping("/api/condominios/{condominiumId}/assistente")
@PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
public class AssistantController {

    private final AssistantQuestionService questions;
    private final AssistantSearchService search;
    private final CondominiumAccess access;
    private final CondominiumService condominiums;

    public AssistantController(AssistantQuestionService questions, AssistantSearchService search,
            CondominiumAccess access, CondominiumService condominiums) {
        this.questions = questions;
        this.search = search;
        this.access = access;
        this.condominiums = condominiums;
    }

    @PostMapping("/perguntas")
    public AssistantAnswerResponse ask(@PathVariable UUID condominiumId,
            @RequestBody(required = false) QuestionRequest request) {
        requireCondominium(condominiumId);
        return questions.ask(condominiumId, request);
    }

    @PostMapping("/busca")
    public List<DocumentChunkResponse> search(@PathVariable UUID condominiumId,
            @RequestBody(required = false) DocumentSearchRequest request) {
        requireCondominium(condominiumId);
        return search.search(condominiumId, request);
    }

    private void requireCondominium(UUID condominiumId) {
        access.require(condominiumId);
        condominiums.requireExists(condominiumId);
    }
}
