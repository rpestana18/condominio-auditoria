package br.com.condominioauditoria.backend.contabil;

import br.com.condominioauditoria.backend.condominio.CondominioRepository;
import br.com.condominioauditoria.backend.seguranca.AcessoCondominio;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fundos do fluxo do condomínio, pelo nome exato impresso no fluxo (RF-03.1.9): a lista para ligar as linhas 1.9 e
 * para o filtro de fundo. Todos os perfis consultam. "OBRAS" e "OBRAS / REFORMAS / INFRA" são itens separados.
 */
@RestController
@RequestMapping("/api/condominios/{condominioId}/fundos")
class FundoController {

    record FundoDto(UUID id, String nome, boolean ordinario) {
    }

    private final AcessoCondominio acesso;
    private final CondominioRepository condominios;
    private final FundoRepository fundos;

    FundoController(AcessoCondominio acesso, CondominioRepository condominios, FundoRepository fundos) {
        this.acesso = acesso;
        this.condominios = condominios;
        this.fundos = fundos;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    @Transactional(readOnly = true)
    List<FundoDto> listar(@PathVariable UUID condominioId) {
        acesso.exigir(condominioId);
        // Sem fundo ordinário confirmado a lista sai com ordinario = false em todos (é dela que o Gestor escolhe)
        UUID ordinario = condominios.findById(condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"))
                .getFundoOrdinarioId();
        return fundos.findByCondominioId(condominioId).stream()
                .sorted(Comparator.comparing(Fundo::getNome))
                .map(f -> new FundoDto(f.getId(), f.getNome(), f.getId().equals(ordinario)))
                .toList();
    }
}
