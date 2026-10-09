package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.orcamento.RealocacaoDtos.PedidoRealocacao;
import br.com.condominioauditoria.api.orcamento.RealocacaoDtos.RealocacaoDto;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Realocação mínima (RF-03.1.7; ADR 0004, Decisão 8): realocar e desfazer só o Gestor e o Admin (o Usuário recebe
 * 403); consultar, todos os perfis do condomínio.
 */
@RestController
@RequestMapping("/api/condominios/{condominioId}/realocacoes")
class RealocacaoController {

    private final CondominiumAccess acesso;
    private final ServicoRealocacao servico;

    RealocacaoController(CondominiumAccess acesso, ServicoRealocacao servico) {
        this.acesso = acesso;
        this.servico = servico;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    List<RealocacaoDto> listar(@PathVariable UUID condominioId, @RequestParam(name = "po") UUID poId) {
        acesso.require(condominioId);
        return servico.listar(condominioId, poId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMIN')")
    RealocacaoDto realocar(@PathVariable UUID condominioId, @RequestBody PedidoRealocacao pedido) {
        acesso.require(condominioId);
        return servico.realocar(condominioId, pedido, acesso.username());
    }

    @DeleteMapping("/{realocacaoId}")
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMIN')")
    RealocacaoDto desfazer(@PathVariable UUID condominioId, @PathVariable UUID realocacaoId) {
        acesso.require(condominioId);
        return servico.desfazer(condominioId, realocacaoId, acesso.username());
    }
}
