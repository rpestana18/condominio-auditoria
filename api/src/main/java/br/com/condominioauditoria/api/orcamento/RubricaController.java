package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.orcamento.RubricaDtos.EventoRubricaDto;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.FiltroRubrica;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.LinhaComRubrica;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.PedidoLoteRubrica;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.PedidoNovaRubrica;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.PedidoRenomear;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.PedidoRubricaLinha;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.ResultadoLoteRubrica;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.ResultadoSugestoesRubrica;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.RubricaDto;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.RubricasDaPo;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rubricas do condomínio e de cada linha das POs confirmadas (RF-11.7; ADR 0005, Decisões 1 e 5). Consultar: todos os
 * perfis do condomínio. Criar, renomear, escolher, confirmar, recusar e sugerir: só o Admin (Gestor e Usuário recebem
 * 403).
 */
@RestController
@RequestMapping("/api/condominios/{condominioId}")
class RubricaController {

    private final CondominiumAccess acesso;
    private final ServicoRubricas servico;

    RubricaController(CondominiumAccess acesso, ServicoRubricas servico) {
        this.acesso = acesso;
        this.servico = servico;
    }

    @GetMapping("/rubricas")
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    List<RubricaDto> catalogo(@PathVariable UUID condominioId) {
        acesso.require(condominioId);
        return servico.catalogo(condominioId);
    }

    @PostMapping("/rubricas")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    RubricaDto criar(@PathVariable UUID condominioId, @RequestBody PedidoNovaRubrica pedido) {
        acesso.require(condominioId);
        return servico.criar(condominioId, pedido, acesso.username());
    }

    @PutMapping("/rubricas/{rubricaId}")
    @PreAuthorize("hasRole('ADMIN')")
    RubricaDto renomear(@PathVariable UUID condominioId, @PathVariable UUID rubricaId,
            @RequestBody PedidoRenomear pedido) {
        acesso.require(condominioId);
        return servico.renomear(condominioId, rubricaId, pedido, acesso.username());
    }

    @GetMapping("/previsoes/{poId}/rubricas")
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    RubricasDaPo listar(@PathVariable UUID condominioId, @PathVariable UUID poId,
            @RequestParam(required = false) FiltroRubrica filtro) {
        acesso.require(condominioId);
        return servico.listar(condominioId, poId, filtro);
    }

    @GetMapping("/previsoes/{poId}/rubricas/eventos")
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    List<EventoRubricaDto> eventos(@PathVariable UUID condominioId, @PathVariable UUID poId) {
        acesso.require(condominioId);
        return servico.eventos(condominioId, poId);
    }

    @PutMapping("/previsoes/{poId}/rubricas/{linhaId}")
    @PreAuthorize("hasRole('ADMIN')")
    LinhaComRubrica definir(@PathVariable UUID condominioId, @PathVariable UUID poId, @PathVariable UUID linhaId,
            @RequestBody PedidoRubricaLinha pedido) {
        acesso.require(condominioId);
        return servico.definir(condominioId, poId, linhaId, pedido, acesso.username());
    }

    @PostMapping("/previsoes/{poId}/rubricas/lote")
    @PreAuthorize("hasRole('ADMIN')")
    ResultadoLoteRubrica lote(@PathVariable UUID condominioId, @PathVariable UUID poId,
            @RequestBody PedidoLoteRubrica pedido) {
        acesso.require(condominioId);
        return servico.lote(condominioId, poId, pedido, acesso.username());
    }

    @PostMapping("/previsoes/{poId}/rubricas/sugestoes")
    @PreAuthorize("hasRole('ADMIN')")
    ResultadoSugestoesRubrica sugerir(@PathVariable UUID condominioId, @PathVariable UUID poId) {
        acesso.require(condominioId);
        return servico.sugerir(condominioId, poId, acesso.username());
    }
}
