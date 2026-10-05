package br.com.condominioauditoria.backend.auditoria;

import br.com.condominioauditoria.backend.auditoria.ConsultaAchados.AchadoDetalhe;
import br.com.condominioauditoria.backend.seguranca.AcessoCondominio;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Achados do condomínio, só leitura, para todos os perfis (marcar estados entra com o RF-02.8). */
@RestController
@RequestMapping("/api/condominios/{condominioId}/achados")
class AchadoController {

    private final AcessoCondominio acesso;
    private final ConsultaAchados consulta;

    AchadoController(AcessoCondominio acesso, ConsultaAchados consulta) {
        this.acesso = acesso;
        this.consulta = consulta;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    List<AchadoDetalhe> listar(@PathVariable UUID condominioId,
            @RequestParam(required = false) String competencia) {
        acesso.exigir(condominioId);
        return consulta.listar(condominioId, competencia);
    }
}
