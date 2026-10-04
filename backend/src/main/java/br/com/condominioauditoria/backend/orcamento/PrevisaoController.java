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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** PO lida e confirmada (RF-03.1.1 a RF-03.1.3). Consultar: todos os perfis do condomínio. Confirmar: só Admin. */
@RestController
@RequestMapping("/api/condominios/{condominioId}/previsoes")
class PrevisaoController {

    private final AcessoCondominio acesso;
    private final ConsultaPrevisao consulta;
    private final ConfirmacaoPrevisao confirmacao;
    private final LigacaoFundosPo ligacao;
    private final EventoPrevisaoRepository eventos;
    private final PrevisaoOrcamentariaRepository previsoes;

    PrevisaoController(AcessoCondominio acesso, ConsultaPrevisao consulta, ConfirmacaoPrevisao confirmacao,
            LigacaoFundosPo ligacao, EventoPrevisaoRepository eventos, PrevisaoOrcamentariaRepository previsoes) {
        this.acesso = acesso;
        this.consulta = consulta;
        this.confirmacao = confirmacao;
        this.ligacao = ligacao;
        this.eventos = eventos;
        this.previsoes = previsoes;
    }

    /** RF-03.1.9: só o Admin altera a ligação das linhas 1.9 depois da confirmação; Gestor e Usuário recebem 403. */
    @PutMapping("/{poId}/fundos")
    @PreAuthorize("hasRole('ADMIN')")
    PrevisaoDetalhe alterarFundos(@PathVariable UUID condominioId, @PathVariable UUID poId,
            @RequestBody LigacaoFundosPo.PedidoFundos pedido) {
        acesso.exigir(condominioId);
        return ligacao.alterar(condominioId, poId, pedido, acesso.usuario());
    }

    /** Trilha da PO (todos os perfis): confirmação, substituição e alterações da ligação dos fundos. */
    @GetMapping("/{poId}/eventos")
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    List<PrevisaoDtos.EventoPrevisaoDto> eventos(@PathVariable UUID condominioId, @PathVariable UUID poId) {
        acesso.exigir(condominioId);
        PrevisaoOrcamentaria po = previsoes.findByIdAndCondominioId(poId, condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO não encontrada"));
        return eventos.findByPrevisaoIdOrderByEm(po.getId()).stream().map(PrevisaoDtos.EventoPrevisaoDto::de).toList();
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

    /** RF-03.1.3: só o Admin confirma; Gestor e Usuário recebem 403. */
    @PostMapping("/{poId}/confirmacao")
    @PreAuthorize("hasRole('ADMIN')")
    PrevisaoDetalhe confirmar(@PathVariable UUID condominioId, @PathVariable UUID poId,
            @RequestBody PedidoConfirmacao pedido) {
        acesso.exigir(condominioId);
        if (pedido == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe os dados da confirmação");
        }
        return confirmacao.confirmar(condominioId, poId, pedido, acesso.usuario());
    }
}
