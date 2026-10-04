package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.Evidencia;
import br.com.condominioauditoria.backend.seguranca.AcessoCondominio;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Previsto × realizado (RF-03.1.6 a RF-03.1.12): todos os perfis do condomínio consultam. Calculado na consulta pela
 * função pura {@link CalculoPrevistoRealizado}; nada é gravado.
 */
@RestController
@RequestMapping("/api/condominios/{condominioId}/previsto-realizado")
class PrevistoRealizadoController {

    private final AcessoCondominio acesso;
    private final ConsultaPrevistoRealizado consulta;
    private final ExportacaoPrevistoRealizado exportacao;

    PrevistoRealizadoController(AcessoCondominio acesso, ConsultaPrevistoRealizado consulta,
            ExportacaoPrevistoRealizado exportacao) {
        this.acesso = acesso;
        this.consulta = consulta;
        this.exportacao = exportacao;
    }

    /** PDF ou Excel da mesma visão (RF-03.1.14): todos os perfis do condomínio exportam. */
    @GetMapping("/exportacao")
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    ResponseEntity<byte[]> exportar(@PathVariable UUID condominioId, @RequestParam String formato,
            @RequestParam String periodo, @RequestParam(name = "po", required = false) UUID poId,
            @RequestParam(name = "fundo", required = false) UUID fundoId) {
        acesso.exigir(condominioId);
        String quem = acesso.nomeCompleto().equals(acesso.usuario()) ? acesso.usuario()
                : acesso.nomeCompleto() + " (" + acesso.usuario() + ")";
        var arquivo = exportacao.exportar(condominioId, periodo, poId, fundoId, formato, quem);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(arquivo.tipo()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(arquivo.nome())
                        .build().toString())
                .body(arquivo.conteudo());
    }

    /**
     * {@code periodo}: AAAA-MM ou "acumulado". {@code po}: versão da PO; sem ela, a que vale no mês. {@code fundo}:
     * o fundo ordinário (só o fundo Condomínio) ou outro fundo (só o painel dele); sem ele, tudo.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    PrevistoRealizado consultar(@PathVariable UUID condominioId, @RequestParam String periodo,
            @RequestParam(name = "po", required = false) UUID poId,
            @RequestParam(name = "fundo", required = false) UUID fundoId) {
        acesso.exigir(condominioId);
        return fundoId == null ? consulta.consultar(condominioId, periodo, poId)
                : consulta.consultar(condominioId, periodo, poId, fundoId);
    }

    @GetMapping("/evidencia")
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    List<Evidencia> evidencia(@PathVariable UUID condominioId, @RequestParam String periodo,
            @RequestParam(name = "po", required = false) UUID poId, @RequestParam String alvo) {
        acesso.exigir(condominioId);
        return consulta.evidencia(condominioId, periodo, poId, alvo);
    }
}
