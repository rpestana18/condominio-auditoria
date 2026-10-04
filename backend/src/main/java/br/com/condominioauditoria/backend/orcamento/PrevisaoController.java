package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.PrevisaoDetalhe;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.PrevisaoResumo;
import br.com.condominioauditoria.backend.seguranca.AcessoCondominio;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** PO lida e confirmada (RF-03.1.1 a RF-03.1.3). Consultar: todos os perfis do condomínio. */
@RestController
@RequestMapping("/api/condominios/{condominioId}/previsoes")
class PrevisaoController {

    private final AcessoCondominio acesso;
    private final ConsultaPrevisao consulta;

    PrevisaoController(AcessoCondominio acesso, ConsultaPrevisao consulta) {
        this.acesso = acesso;
        this.consulta = consulta;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    List<PrevisaoResumo> listar(@PathVariable UUID condominioId) {
        acesso.exigir(condominioId);
        return consulta.listar(condominioId);
    }

    @GetMapping("/{poId}")
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    PrevisaoDetalhe detalhe(@PathVariable UUID condominioId, @PathVariable UUID poId) {
        acesso.exigir(condominioId);
        return consulta.detalhe(condominioId, poId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO não encontrada"));
    }
}
