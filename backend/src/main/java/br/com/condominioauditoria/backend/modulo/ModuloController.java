package br.com.condominioauditoria.backend.modulo;

import br.com.condominioauditoria.backend.condominio.Condominio;
import br.com.condominioauditoria.backend.condominio.CondominioRepository;
import br.com.condominioauditoria.backend.ia.CatalogoIa;
import br.com.condominioauditoria.backend.ia.ConfiguracaoIaServico;
import br.com.condominioauditoria.backend.ia.ConfiguracaoIaServico.ContextoAssistente;
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
    private final ConfiguracaoIaServico configuracaoIa;
    private final CatalogoIa catalogoIa;

    ModuloController(Modulos modulos, RegistroUso registroUso, AcessoCondominio acesso,
            CondominioRepository condominios, ConfiguracaoIaServico configuracaoIa, CatalogoIa catalogoIa) {
        this.modulos = modulos;
        this.registroUso = registroUso;
        this.acesso = acesso;
        this.condominios = condominios;
        this.configuracaoIa = configuracaoIa;
        this.catalogoIa = catalogoIa;
    }

    /**
     * O que a tela precisa para montar o menu: módulos ligados (RF-10.2, RF-10.3) e, com o Assistente ligado, o modo
     * de IA efetivo dele (RF-04.16), sem chave.
     */
    @GetMapping("/contexto")
    ContextoCondominio contexto(@PathVariable UUID condominioId) {
        Condominio condominio = condominio(condominioId);
        return new ContextoCondominio(condominio.getId(), condominio.getNome(), modulos.ligados(condominioId),
                configuracaoIa.contexto(condominioId));
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
        ResumoUso resumo = registroUso.resumo(condominioId, inicio, fim);
        return UsoDto.de(resumo, custo(condominioId, inicio, fim));
    }

    /** Custo estimado do período; nulo se o rag não respondeu o catálogo de preços (o uso sai mesmo assim). */
    private CustoUso.CustoDoPeriodo custo(UUID condominioId, LocalDate inicio, LocalDate fim) {
        return acesso.tokenBearer().flatMap(catalogoIa::precos)
                .map(precos -> registroUso.custo(condominioId, inicio, fim, precos))
                .orElse(null);
    }

    /**
     * Períodos ativos (que tocam o período) e uso por mês, em Excel com duas abas (RF-10.6, RF-09.7), com o custo
     * estimado em US$ (tokens × preço do catálogo do rag). Rag fora do ar: a planilha sai sem custo, com aviso.
     */
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
        CustoUso.CustoDoPeriodo custo = custo(condominioId, inicio, fim);
        String nome = "uso-modulos-%s-a-%s.xlsx".formatted(inicio, fim);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ExportacaoUsoExcel.TIPO))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .body(ExportacaoUsoExcel.gerar(condominio.getNome(), uso, periodos, custo));
    }

    private Condominio condominio(UUID condominioId) {
        acesso.exigir(condominioId);
        return condominios.findById(condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
    }

    /** assistente nulo = módulo Assistente desligado (contracts/openapi.yaml, ContextoCondominio). */
    record ContextoCondominio(UUID condominioId, String nome, List<String> modulosLigados,
            ContextoAssistente assistente) {
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

    /**
     * Linha de uso. custoEstimadoUsd: ausente sem tokens ou com o custo indisponível; nulo com modelo sem preço;
     * senão texto decimal com 2 casas ("3.50"), formatado do BigDecimal.
     */
    record TotalDto(@JsonInclude(JsonInclude.Include.NON_NULL) String mes, String modulo, String funcao,
            long quantidade, long tokensEntrada, long tokensSaida, long arquivos, long paginas,
            @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = Ausente.class) String custoEstimadoUsd) {

        static TotalDto de(TotalUso t, String custo) {
            return new TotalDto(t.mes(), t.modulo(), t.funcao().codigo(), t.quantidade(), t.tokensEntrada(),
                    t.tokensSaida(), t.arquivos(), t.paginas(), custo);
        }
    }

    /**
     * Marca de "campo ausente" no JSON (diferente de nulo): o Jackson pula o campo quando o valor é esta instância.
     * Comparação por identidade, de propósito.
     */
    static final class Ausente {
        @SuppressWarnings("StringOperationCanBeSimplified")
        static final String VALOR = new String("ausente");

        @Override
        @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
        public boolean equals(Object outro) {
            return outro == VALOR;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    /** custoDisponivel = false: o rag não respondeu o catálogo de preços; nenhum valor de custo vai no JSON. */
    record UsoDto(UUID condominioId, LocalDate inicio, LocalDate fim, List<TotalDto> porFuncao, List<TotalDto> porMes,
            boolean custoDisponivel,
            @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = Ausente.class) String custoEstimadoTotalUsd,
            List<String> modelosSemPreco) {

        static UsoDto de(ResumoUso u) {
            return de(u, null);
        }

        static UsoDto de(ResumoUso u, CustoUso.CustoDoPeriodo custo) {
            return new UsoDto(u.condominioId(), u.inicio(), u.fim(),
                    u.porFuncao().stream().map(t -> TotalDto.de(t, custoDaLinha(t, custo, false))).toList(),
                    u.porMes().stream().map(t -> TotalDto.de(t, custoDaLinha(t, custo, true))).toList(),
                    custo != null,
                    custo == null ? Ausente.VALOR : texto(custo.total()),
                    custo == null ? List.of() : List.copyOf(custo.modelosSemPreco()));
        }

        private static String custoDaLinha(TotalUso t, CustoUso.CustoDoPeriodo custo, boolean porMes) {
            if (custo == null || (t.tokensEntrada() == 0 && t.tokensSaida() == 0)) {
                return Ausente.VALOR;
            }
            return texto(porMes ? custo.doMes(t) : custo.daFuncao(t));
        }

        private static String texto(java.math.BigDecimal valor) {
            return valor == null ? null : valor.toPlainString();
        }
    }
}
