package br.com.condominioauditoria.api.painel;

import br.com.condominioauditoria.api.condominio.Condominio;
import br.com.condominioauditoria.api.condominio.CondominioRepository;
import br.com.condominioauditoria.api.contabil.FundoRepository;
import br.com.condominioauditoria.api.seguranca.AcessoCondominio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** O Gestor ou o Admin confirma qual fundo é o ordinário do condomínio (RF-05.1b). */
@RestController
@RequestMapping("/api/condominios/{condominioId}/fundo-ordinario")
class FundoOrdinarioController {

    private static final Logger log = LoggerFactory.getLogger(FundoOrdinarioController.class);

    private final AcessoCondominio acesso;
    private final CondominioRepository condominios;
    private final FundoRepository fundos;

    FundoOrdinarioController(AcessoCondominio acesso, CondominioRepository condominios, FundoRepository fundos) {
        this.acesso = acesso;
        this.condominios = condominios;
        this.fundos = fundos;
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMIN')")
    @Transactional
    ResponseEntity<Void> confirmar(@PathVariable UUID condominioId, @Valid @RequestBody Confirmacao pedido) {
        acesso.exigir(condominioId);
        var fundo = fundos.findById(pedido.fundoId())
                .filter(f -> f.getCondominioId().equals(condominioId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fundo não encontrado"));
        Condominio condominio = condominios.findById(condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        UUID anterior = condominio.getFundoOrdinarioId();
        condominio.confirmarFundoOrdinario(fundo.getId(), acesso.usuario(), Instant.now());
        // Até a trilha de auditoria (RF-07.4) existir, o registro fica no log e nas colunas de confirmação
        log.info("Fundo ordinário confirmado: condominio={} fundo={} ({}) anterior={} por={}",
                condominioId, fundo.getId(), fundo.getNome(), anterior, acesso.usuario());
        return ResponseEntity.noContent().build();
    }

    record Confirmacao(@NotNull UUID fundoId) {
    }
}
