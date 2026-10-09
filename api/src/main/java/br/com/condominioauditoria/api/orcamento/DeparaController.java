package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.orcamento.DeparaDtos.ContaDepara;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.DeparaLista;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.EventoDeparaDto;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.FiltroDepara;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.PedidoDestino;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.PedidoLote;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.ResultadoLote;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.ResultadoPlanilha;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.ResultadoSugestoes;
import br.com.condominioauditoria.api.seguranca.AcessoCondominio;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * De-para das contas do fluxo (RF-03.1.4, RF-03.1.5 e RF-03.1.13). Consultar: todos os perfis do condomínio. Criar,
 * alterar, confirmar, recusar, sugerir e carregar planilha: só o Admin (Gestor e Usuário recebem 403).
 */
@RestController
@RequestMapping("/api/condominios/{condominioId}/previsoes/{poId}/depara")
class DeparaController {

    /** A planilha do piloto tem 3 KB; 1 MB é folga para milhares de contas. */
    static final long TAMANHO_MAXIMO_PLANILHA = 1024 * 1024;

    private final AcessoCondominio acesso;
    private final ServicoDepara servico;

    DeparaController(AcessoCondominio acesso, ServicoDepara servico) {
        this.acesso = acesso;
        this.servico = servico;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    DeparaLista listar(@PathVariable UUID condominioId, @PathVariable UUID poId,
            @RequestParam(required = false) FiltroDepara filtro) {
        acesso.exigir(condominioId);
        return servico.listar(condominioId, poId, filtro);
    }

    @GetMapping("/eventos")
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    List<EventoDeparaDto> eventos(@PathVariable UUID condominioId, @PathVariable UUID poId) {
        acesso.exigir(condominioId);
        return servico.eventos(condominioId, poId);
    }

    @PutMapping("/{conta}")
    @PreAuthorize("hasRole('ADMIN')")
    ContaDepara definir(@PathVariable UUID condominioId, @PathVariable UUID poId, @PathVariable String conta,
            @RequestBody PedidoDestino pedido) {
        acesso.exigir(condominioId);
        return servico.definir(condominioId, poId, conta, pedido, acesso.usuario());
    }

    @PostMapping("/lote")
    @PreAuthorize("hasRole('ADMIN')")
    ResultadoLote lote(@PathVariable UUID condominioId, @PathVariable UUID poId, @RequestBody PedidoLote pedido) {
        acesso.exigir(condominioId);
        return servico.lote(condominioId, poId, pedido, acesso.usuario());
    }

    @PostMapping("/sugestoes")
    @PreAuthorize("hasRole('ADMIN')")
    ResultadoSugestoes sugerir(@PathVariable UUID condominioId, @PathVariable UUID poId) {
        acesso.exigir(condominioId);
        return servico.sugerir(condominioId, poId, acesso.usuario());
    }

    @PostMapping(path = "/planilha", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    ResultadoPlanilha planilha(@PathVariable UUID condominioId, @PathVariable UUID poId,
            @RequestPart("arquivo") MultipartFile arquivo) throws IOException {
        acesso.exigir(condominioId);
        if (arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "A planilha está vazia");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO_PLANILHA) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "A planilha passa de 1 MB");
        }
        return servico.carregarPlanilha(condominioId, poId, arquivo.getOriginalFilename(), texto(arquivo.getBytes()),
                acesso.usuario());
    }

    /** UTF-8 (padrão); se não for UTF-8 válido, Windows-1252, como o Excel em português salva o CSV. */
    static String texto(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, java.nio.charset.Charset.forName("windows-1252"));
        }
    }
}
