package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.orcamento.ExercicioDtos.ConferenciaColuna;
import br.com.condominioauditoria.backend.orcamento.ExercicioDtos.Exercicio;
import br.com.condominioauditoria.backend.seguranca.AcessoCondominio;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exercícios do menu "Análise da PO" e a conferência da coluna impressa (RF-11.4 e RF-11.5). Todos os perfis. */
@RestController
@RequestMapping("/api/condominios/{condominioId}")
class ExercicioController {

    private final AcessoCondominio acesso;
    private final ServicoExercicios servico;

    ExercicioController(AcessoCondominio acesso, ServicoExercicios servico) {
        this.acesso = acesso;
        this.servico = servico;
    }

    @GetMapping("/exercicios")
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    List<Exercicio> listar(@PathVariable UUID condominioId) {
        acesso.exigir(condominioId);
        return servico.listar(condominioId);
    }

    @GetMapping("/previsoes/{poId}/coluna-impressa")
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    ConferenciaColuna colunaImpressa(@PathVariable UUID condominioId, @PathVariable UUID poId) {
        acesso.exigir(condominioId);
        return servico.colunaImpressa(condominioId, poId);
    }
}
