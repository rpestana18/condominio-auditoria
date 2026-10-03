package br.com.condominioauditoria.backend.arquivo;

import br.com.condominioauditoria.backend.arquivo.ArquivoDtos.ArquivoDetalhe;
import br.com.condominioauditoria.backend.arquivo.ArquivoDtos.ArquivoResumo;
import br.com.condominioauditoria.backend.arquivo.ArquivoDtos.CategoriaDto;
import br.com.condominioauditoria.backend.arquivo.ArquivoDtos.ConferenciaDto;
import br.com.condominioauditoria.backend.arquivo.ArquivoDtos.SaldoDto;
import br.com.condominioauditoria.backend.contabil.ConferenciaRepository;
import br.com.condominioauditoria.backend.contabil.Fundo;
import br.com.condominioauditoria.backend.contabil.FundoRepository;
import br.com.condominioauditoria.backend.contabil.SaldoFundoRepository;
import br.com.condominioauditoria.backend.seguranca.AcessoCondominio;
import br.com.condominioauditoria.armazenamento.Armazenamento;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
class ArquivoController {

    private final ArquivoRepository arquivos;
    private final ArquivoService servico;
    private final AcessoCondominio acesso;
    private final Armazenamento armazenamento;
    private final ConferenciaRepository conferencias;
    private final SaldoFundoRepository saldos;
    private final FundoRepository fundos;

    ArquivoController(ArquivoRepository arquivos, ArquivoService servico, AcessoCondominio acesso,
            Armazenamento armazenamento, ConferenciaRepository conferencias, SaldoFundoRepository saldos,
            FundoRepository fundos) {
        this.arquivos = arquivos;
        this.servico = servico;
        this.acesso = acesso;
        this.armazenamento = armazenamento;
        this.conferencias = conferencias;
        this.saldos = saldos;
        this.fundos = fundos;
    }

    @GetMapping("/categorias")
    List<CategoriaDto> categorias() {
        return Arrays.stream(Categoria.values()).map(c -> new CategoriaDto(c, c.rotulo())).toList();
    }

    /** Lista do mais recente para o mais antigo, opcionalmente filtrada por categoria. */
    @GetMapping("/condominios/{condominioId}/arquivos")
    List<ArquivoResumo> listar(@PathVariable UUID condominioId, @RequestParam(required = false) Categoria categoria) {
        acesso.exigir(condominioId);
        var lista = categoria == null
                ? arquivos.findByCondominioIdOrderByEnviadoEmDesc(condominioId)
                : arquivos.findByCondominioIdAndCategoriaOrderByEnviadoEmDesc(condominioId, categoria);
        return lista.stream().map(ArquivoResumo::de).toList();
    }

    /** Para o indicador discreto de "último arquivo" no canto da tela. */
    @GetMapping("/condominios/{condominioId}/arquivos/ultimo")
    ResponseEntity<ArquivoResumo> ultimo(@PathVariable UUID condominioId) {
        acesso.exigir(condominioId);
        return arquivos.findFirstByCondominioIdOrderByEnviadoEmDesc(condominioId)
                .map(a -> ResponseEntity.ok(ArquivoResumo.de(a)))
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/condominios/{condominioId}/arquivos/{id}")
    ArquivoDetalhe detalhe(@PathVariable UUID condominioId, @PathVariable UUID id) {
        Arquivo arquivo = buscar(condominioId, id);
        Map<UUID, String> nomes = fundos.findByCondominioId(condominioId).stream()
                .collect(Collectors.toMap(Fundo::getId, Fundo::getNome));
        return new ArquivoDetalhe(ArquivoResumo.de(arquivo), arquivo.getSha256(),
                conferencias.findByArquivoIdOrderByOrdem(id).stream().map(ConferenciaDto::de).toList(),
                saldos.findByArquivoId(id).stream()
                        .map(s -> new SaldoDto(nomes.get(s.getFundoId()), s.getSaldoAnterior(), s.getCreditos(),
                                s.getDebitos(), s.getSaldoAtual()))
                        .toList());
    }

    /** Baixa o original, exatamente como foi enviado. */
    @GetMapping("/condominios/{condominioId}/arquivos/{id}/conteudo")
    ResponseEntity<InputStreamResource> conteudo(@PathVariable UUID condominioId, @PathVariable UUID id) throws IOException {
        Arquivo arquivo = buscar(condominioId, id);
        MediaType tipo = arquivo.getTipoConteudo() == null ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(arquivo.getTipoConteudo());
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(arquivo.getNomeOriginal()).build().toString())
                .body(new InputStreamResource(armazenamento.abrir(arquivo.getCaminho())));
    }

    @PostMapping(path = "/condominios/{condominioId}/arquivos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMIN')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ArquivoResumo enviar(@PathVariable UUID condominioId, @RequestParam Categoria categoria,
            @RequestPart("arquivo") MultipartFile arquivo) throws IOException {
        acesso.exigir(condominioId);
        if (arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo vazio");
        }
        return ArquivoResumo.de(servico.receber(condominioId, categoria, arquivo, acesso.usuario()));
    }

    @PostMapping("/condominios/{condominioId}/arquivos/{id}/reprocessar")
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMIN')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ArquivoResumo reprocessar(@PathVariable UUID condominioId, @PathVariable UUID id) {
        return ArquivoResumo.de(servico.reprocessar(buscar(condominioId, id)));
    }

    private Arquivo buscar(UUID condominioId, UUID id) {
        acesso.exigir(condominioId);
        return arquivos.findByIdAndCondominioId(id, condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado"));
    }
}
