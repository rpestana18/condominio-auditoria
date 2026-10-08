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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exercícios do menu "Análise da PO", a conferência da coluna impressa e a comparação (RF-11.4 a RF-11.6). Todos os
 * perfis.
 */
@RestController
@RequestMapping("/api/condominios/{condominioId}")
class ExercicioController {

    private final AcessoCondominio acesso;
    private final ServicoExercicios servico;
    private final ServicoComparacao comparacao;

    ExercicioController(AcessoCondominio acesso, ServicoExercicios servico, ServicoComparacao comparacao) {
        this.acesso = acesso;
        this.servico = servico;
        this.comparacao = comparacao;
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

    /** Comparar exercícios (RF-11.6): {@code exercicios} separados por vírgula; vazio = os dois mais recentes. */
    @GetMapping("/comparacao-exercicios")
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    ComparacaoExercicios.Resultado comparar(@PathVariable UUID condominioId,
            @RequestParam(required = false) List<String> exercicios, @RequestParam(required = false) UUID fundo,
            @RequestParam(defaultValue = "false") boolean mesmosMeses) {
        acesso.exigir(condominioId);
        return comparacao.comparar(condominioId, exercicios, fundo, mesmosMeses);
    }
}
