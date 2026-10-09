package br.com.condominioauditoria.api.ia;

import br.com.condominioauditoria.api.condominio.CondominioRepository;
import br.com.condominioauditoria.api.ia.CatalogoIa.ModeloIa;
import br.com.condominioauditoria.api.ia.CatalogoIa.ProvedorIa;
import br.com.condominioauditoria.api.ia.ConfiguracaoIaServico.Efetiva;
import br.com.condominioauditoria.api.ia.ConfiguracaoIaServico.Pedido;
import br.com.condominioauditoria.api.ia.ConfiguracaoIaServico.PedidoEmbeddings;
import br.com.condominioauditoria.api.ia.ConfiguracaoIaServico.PedidoRespostas;
import br.com.condominioauditoria.api.modulo.ModoIa;
import br.com.condominioauditoria.api.modulo.PedidoInvalidoException;
import br.com.condominioauditoria.api.seguranca.AcessoCondominio;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Configuração de IA do condomínio e catálogo de provedores (RF-09.1, RF-09.2, RF-09.6). Tudo só ADMIN: Usuário e
 * Gestor não veem nem alteram (o modo efetivo, sem chave, sai no contexto do condomínio). A chave é só de escrita.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
class IaController {

    private final ConfiguracaoIaServico servico;
    private final CatalogoIa catalogo;
    private final AcessoCondominio acesso;
    private final CondominioRepository condominios;

    IaController(ConfiguracaoIaServico servico, CatalogoIa catalogo, AcessoCondominio acesso,
            CondominioRepository condominios) {
        this.servico = servico;
        this.catalogo = catalogo;
        this.acesso = acesso;
        this.condominios = condominios;
    }

    /** Catálogo para os campos da tela, na ordem do rag, sem a chave pública. */
    @GetMapping("/ia/provedores")
    List<ProvedorDto> provedores() {
        return catalogo.ler(token()).provedores().stream().map(ProvedorDto::de).toList();
    }

    @GetMapping("/condominios/{condominioId}/ia")
    ConfiguracaoDto ler(@PathVariable UUID condominioId) {
        exigirCondominio(condominioId);
        return ConfiguracaoDto.de(servico.ler(condominioId));
    }

    @PutMapping("/condominios/{condominioId}/ia")
    ConfiguracaoDto gravar(@PathVariable UUID condominioId, @RequestBody(required = false) PedidoDto pedido) {
        exigirCondominio(condominioId);
        if (pedido == null || pedido.assistente() == null) {
            throw new PedidoInvalidoException("Informe o modo geral e a configuração do Assistente");
        }
        return ConfiguracaoDto.de(servico.gravar(condominioId, pedido.paraServico(), acesso.usuario(), token()));
    }

    private void exigirCondominio(UUID condominioId) {
        acesso.exigir(condominioId);
        if (!condominios.existsById(condominioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado");
        }
    }

    private String token() {
        return acesso.tokenBearer().orElseThrow(() -> new IllegalStateException("Token ausente"));
    }

    // ---- Contrato (contracts/openapi.yaml: ProvedorIa, ConfiguracaoIa, PedidoConfiguracaoIa) ----

    record ModeloDto(String id, String nome, boolean padrao, String precoEntradaMilhaoUsd,
            String precoSaidaMilhaoUsd) {

        static ModeloDto de(ModeloIa m) {
            return new ModeloDto(m.id(), m.nome(), m.padrao(), texto(m.precoEntradaMilhaoUsd()),
                    texto(m.precoSaidaMilhaoUsd()));
        }

        private static String texto(BigDecimal preco) {
            return preco == null ? "0" : preco.toPlainString();
        }
    }

    record ProvedorDto(String codigo, String nome, String tipo, FuncaoIa uso, boolean local, boolean precisaChave,
            Integer dimensao, List<ModeloDto> modelos) {

        static ProvedorDto de(ProvedorIa p) {
            return new ProvedorDto(p.codigo(), p.nome(), p.tipo(), p.uso(), p.local(), p.precisaChave(), p.dimensao(),
                    p.modelos().stream().map(ModeloDto::de).toList());
        }
    }

    record RespostasDto(ModoIa modo, ModoIa modoEfetivo, String provedor, String modelo, boolean chaveCadastrada,
            String chaveFinal) {
    }

    record EmbeddingsDto(ModoIa modo, String provedor, String modelo) {
    }

    record AssistenteDto(RespostasDto respostas, EmbeddingsDto embeddings) {
    }

    /** Resposta: nunca leva a chave, nem cifrada; só chaveCadastrada e os 4 últimos caracteres. */
    record ConfiguracaoDto(ModoIa modoGeral, AssistenteDto assistente, String atualizadoPor, Instant atualizadoEm) {

        static ConfiguracaoDto de(Efetiva e) {
            var r = e.respostas();
            return new ConfiguracaoDto(e.modoGeral(), new AssistenteDto(
                    new RespostasDto(r.modo(), r.modoEfetivo(), r.provedor(), r.modelo(), r.chaveCadastrada(),
                            r.chaveCadastrada() ? r.chaveFinal() : null),
                    new EmbeddingsDto(e.embeddings().modo(), e.embeddings().provedor(), e.embeddings().modelo())),
                    e.atualizadoPor(), e.atualizadoEm());
        }
    }

    record PedidoRespostasDto(ModoIa modo, String provedor, String modelo, String chave, Boolean removerChave) {

        @Override
        public String toString() {
            return "PedidoRespostasDto[" + modo + ", " + provedor + "/" + modelo + ", chave "
                    + (chave == null ? "não enviada" : "enviada") + ", removerChave " + removerChave + "]";
        }
    }

    record PedidoEmbeddingsDto(ModoIa modo, String provedor, String modelo) {
    }

    record PedidoAssistenteDto(PedidoRespostasDto respostas, PedidoEmbeddingsDto embeddings) {
    }

    record PedidoDto(ModoIa modoGeral, PedidoAssistenteDto assistente) {

        Pedido paraServico() {
            var r = assistente.respostas();
            var e = assistente.embeddings();
            return new Pedido(modoGeral,
                    r == null ? null : new PedidoRespostas(r.modo(), r.provedor(), r.modelo(), r.chave(),
                            Boolean.TRUE.equals(r.removerChave())),
                    e == null ? null : new PedidoEmbeddings(e.modo(), e.provedor(), e.modelo()));
        }
    }
}
