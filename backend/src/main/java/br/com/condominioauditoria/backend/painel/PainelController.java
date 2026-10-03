package br.com.condominioauditoria.backend.painel;

import br.com.condominioauditoria.backend.painel.PainelService.Painel;
import br.com.condominioauditoria.backend.seguranca.AcessoCondominio;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Números da tela inicial. Sem fluxo de caixa lido ainda, responde 204. */
@RestController
@RequestMapping("/api/condominios/{condominioId}/painel")
class PainelController {

    private final AcessoCondominio acesso;
    private final PainelService painel;

    PainelController(AcessoCondominio acesso, PainelService painel) {
        this.acesso = acesso;
        this.painel = painel;
    }

    @GetMapping
    ResponseEntity<Painel> painel(@PathVariable UUID condominioId) {
        acesso.exigir(condominioId);
        return painel.ultimo(condominioId).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }
}
