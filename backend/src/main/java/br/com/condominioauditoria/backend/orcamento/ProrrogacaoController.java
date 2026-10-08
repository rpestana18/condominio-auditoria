package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.PedidoProrrogacao;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.PrevisaoDetalhe;
import br.com.condominioauditoria.backend.seguranca.AcessoCondominio;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PO prorrogada (RF-11.3; ADR 0005, Decisões 4 e 5). Marcar e desfazer: só o Admin (Gestor e Usuário recebem 403). A
 * prorrogação aparece no resumo da PO, que todos os perfis consultam.
 */
@RestController
@RequestMapping("/api/condominios/{condominioId}/previsoes/{poId}/prorrogacao")
class ProrrogacaoController {

    private final AcessoCondominio acesso;
    private final ServicoProrrogacao servico;

    ProrrogacaoController(AcessoCondominio acesso, ServicoProrrogacao servico) {
        this.acesso = acesso;
        this.servico = servico;
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    PrevisaoDetalhe prorrogar(@PathVariable UUID condominioId, @PathVariable UUID poId,
            @RequestBody PedidoProrrogacao pedido) {
        acesso.exigir(condominioId);
        return servico.prorrogar(condominioId, poId, pedido, acesso.usuario());
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    PrevisaoDetalhe desfazer(@PathVariable UUID condominioId, @PathVariable UUID poId) {
        acesso.exigir(condominioId);
        return servico.desfazer(condominioId, poId, acesso.usuario());
    }
}
