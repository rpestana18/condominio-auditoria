package br.com.condominioauditoria.backend.modulo;

import br.com.condominioauditoria.backend.condominio.Condominio;
import br.com.condominioauditoria.backend.condominio.CondominioRepository;
import br.com.condominioauditoria.backend.modulo.Modulos.EstadoModulo;
import br.com.condominioauditoria.backend.modulo.RegistroUso.ResumoUso;
import br.com.condominioauditoria.backend.seguranca.AcessoCondominio;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Módulos contratáveis e uso por condomínio (RF-10, RF-09.7). Permissões (§4 e RF-10.2):
 * <ul>
 * <li>contexto e lista de módulos: qualquer perfil com acesso ao condomínio, só leitura;</li>
 * <li>ligar/desligar, trilha, períodos ativos, uso e exportação: só ADMIN.</li>
 * </ul>
 * No MVP o ADMIN é o administrador da plataforma ("tudo, em todos os condomínios") e faz o papel do Super-admin do
 * RF-10.2; o uso também fica com ele até existir o Admin por condomínio (Q17).
 */
@RestController
@RequestMapping("/api/condominios/{condominioId}")
class ModuloController {

    private final Modulos modulos;
    private final RegistroUso registroUso;
    private final AcessoCondominio acesso;
    private final CondominioRepository condominios;

    ModuloController(Modulos modulos, RegistroUso registroUso, AcessoCondominio acesso,
            CondominioRepository condominios) {
        this.modulos = modulos;
        this.registroUso = registroUso;
        this.acesso = acesso;
        this.condominios = condominios;
    }

    /** O que a tela precisa para montar o menu: módulos ligados (RF-10.2, RF-10.3). */
    @GetMapping("/contexto")
    ContextoCondominio contexto(@PathVariable UUID condominioId) {
        Condominio condominio = condominio(condominioId);
        return new ContextoCondominio(condominio.getId(), condominio.getNome(), modulos.ligados(condominioId));
    }

    @GetMapping("/modulos")
    List<ModuloDoCondominio> listar(@PathVariable UUID condominioId) {
        acesso.exigir(condominioId);
        return modulos.estados(condominioId).stream().map(ModuloDoCondominio::de).toList();
    }

    @PutMapping("/modulos/{codigo}")
    @PreAuthorize("hasRole('ADMIN')")
    ModuloDoCondominio alterar(@PathVariable UUID condominioId, @PathVariable String codigo,
            @Valid @RequestBody AlteracaoModulo pedido) {
        condominio(condominioId);
        return ModuloDoCondominio.de(modulos.alterar(condominioId, codigo, pedido.ligado(), pedido.motivo(),
                acesso.usuario()));
    }

    @GetMapping("/modulos/{codigo}/eventos")
    @PreAuthorize("hasRole('ADMIN')")
    List<EventoDto> eventos(@PathVariable UUID condominioId, @PathVariable String codigo) {
        acesso.exigir(condominioId);
        return modulos.eventos(condominioId, codigo).stream().map(EventoDto::de).toList();
    }

    @GetMapping("/modulos/{codigo}/periodos")
    @PreAuthorize("hasRole('ADMIN')")
    List<PeriodoAtivo> periodos(@PathVariable UUID condominioId, @PathVariable String codigo) {
        acesso.exigir(condominioId);
        return modulos.periodos(condominioId, codigo);
    }

    @GetMapping("/uso")
    @PreAuthorize("hasRole('ADMIN')")
    UsoDto uso(@PathVariable UUID condominioId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        acesso.exigir(condominioId);
        return UsoDto.de(registroUso.resumo(condominioId, inicio, fim));
    }

    /** Períodos ativos (que tocam o período) e uso por mês, em Excel com duas abas (RF-10.6, RF-09.7). */
    @GetMapping("/uso/exportacao")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<byte[]> exportar(@PathVariable UUID condominioId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        Condominio condominio = condominio(condominioId);
        ResumoUso uso = registroUso.resumo(condominioId, inicio, fim);
        Instant de = RegistroUso.inicioDoDia(inicio);
        Instant ate = RegistroUso.inicioDoDia(fim.plusDays(1));
        List<PeriodoAtivo> periodos = modulos.catalogo().modulos().stream()
                .flatMap(m -> modulos.periodos(condominioId, m.codigo()).stream())
                .filter(p -> p.tocaIntervalo(de, ate))
                .toList();
        String nome = "uso-modulos-%s-a-%s.xlsx".formatted(inicio, fim);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ExportacaoUsoExcel.TIPO))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .body(ExportacaoUsoExcel.gerar(condominio.getNome(), uso, periodos));
    }

    private Condominio condominio(UUID condominioId) {
        acesso.exigir(condominioId);
        return condominios.findById(condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
    }

    record ContextoCondominio(UUID condominioId, String nome, List<String> modulosLigados) {
    }

    record ModuloDoCondominio(String codigo, String nome, String descricao, List<String> inclui, List<String> dependeDe,
            boolean ligadoPorPadrao, boolean ligado, Instant desde, int versaoCatalogo) {

        static ModuloDoCondominio de(EstadoModulo e) {
            var d = e.definicao();
            return new ModuloDoCondominio(d.codigo(), d.nome(), d.descricao(), d.inclui(), d.dependeDe(),
                    d.ligadoPorPadrao(), e.ligado(), e.desde(), e.versaoCatalogo());
        }
    }

    record AlteracaoModulo(@NotNull(message = "Informe se o módulo fica ligado") Boolean ligado,
            @Size(max = Modulos.MOTIVO_MAXIMO) String motivo) {
    }

    record EventoDto(UUID id, String modulo, boolean ligadoAntes, boolean ligadoDepois, String usuario, Instant quando,
            String motivo) {

        static EventoDto de(EventoModulo e) {
            return new EventoDto(e.getId(), e.getModulo(), e.isLigadoAntes(), e.isLigadoDepois(), e.getUsuario(),
                    e.getQuando(), e.getMotivo());
        }
    }

    record TotalDto(@JsonInclude(JsonInclude.Include.NON_NULL) String mes, String modulo, String funcao, long quantidade, long tokensEntrada, long tokensSaida,
            long arquivos, long paginas) {

        static TotalDto de(TotalUso t) {
            return new TotalDto(t.mes(), t.modulo(), t.funcao().codigo(), t.quantidade(), t.tokensEntrada(),
                    t.tokensSaida(), t.arquivos(), t.paginas());
        }
    }

    record UsoDto(UUID condominioId, LocalDate inicio, LocalDate fim, List<TotalDto> porFuncao, List<TotalDto> porMes) {

        static UsoDto de(ResumoUso u) {
            return new UsoDto(u.condominioId(), u.inicio(), u.fim(), u.porFuncao().stream().map(TotalDto::de).toList(),
                    u.porMes().stream().map(TotalDto::de).toList());
        }
    }
}
