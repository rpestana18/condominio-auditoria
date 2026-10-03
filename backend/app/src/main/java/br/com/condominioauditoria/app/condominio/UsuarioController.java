package br.com.condominioauditoria.app.condominio;

import br.com.condominioauditoria.app.seguranca.AcessoCondominio;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Quem está logado, com quais perfis e em quais condomínios. O frontend monta o menu a partir disto. */
@RestController
@RequestMapping("/api/eu")
class UsuarioController {

    private final AcessoCondominio acesso;
    private final CondominioRepository condominios;

    UsuarioController(AcessoCondominio acesso, CondominioRepository condominios) {
        this.acesso = acesso;
        this.condominios = condominios;
    }

    @GetMapping
    UsuarioLogado eu() {
        List<CondominioResumo> visiveis = condominios.findAll().stream()
                .filter(c -> acesso.podeAcessar(c.getId()))
                .map(c -> new CondominioResumo(c.getId(), c.getNome()))
                .toList();
        return new UsuarioLogado(acesso.usuario(), acesso.nomeCompleto(), acesso.perfis(), visiveis);
    }

    record UsuarioLogado(String usuario, String nome, List<String> perfis, List<CondominioResumo> condominios) {
    }

    record CondominioResumo(UUID id, String nome) {
    }
}
