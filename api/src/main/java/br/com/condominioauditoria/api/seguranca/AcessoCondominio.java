package br.com.condominioauditoria.api.seguranca;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Isolamento entre condomínios: o token traz a lista "condominios" a que o usuário tem acesso.
 * Admin vê todos.
 */
@Component
public class AcessoCondominio {

    public void exigir(UUID condominioId) {
        if (!podeAcessar(condominioId)) {
            throw new AccessDeniedException("Sem acesso a este condomínio");
        }
    }

    public boolean podeAcessar(UUID condominioId) {
        return ehAdmin() || condominiosDoToken().contains(condominioId.toString());
    }

    public boolean ehAdmin() {
        return autenticacao().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    public List<String> condominiosDoToken() {
        if (autenticacao().getPrincipal() instanceof Jwt jwt) {
            List<String> lista = jwt.getClaimAsStringList("condominios");
            return lista == null ? List.of() : lista;
        }
        return List.of();
    }

    public String usuario() {
        return autenticacao().getName();
    }

    public String nomeCompleto() {
        if (autenticacao().getPrincipal() instanceof Jwt jwt && jwt.hasClaim("name")) {
            return jwt.getClaimAsString("name");
        }
        return usuario();
    }

    /**
     * Token do usuário da chamada, pronto para o cabeçalho "authorization" ("Bearer ..."). Usado para repassar o
     * mesmo usuário ao rag, que valida o token de novo.
     */
    public Optional<String> tokenBearer() {
        Authentication autenticacao = autenticacao();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof Jwt jwt) {
            return Optional.of("Bearer " + jwt.getTokenValue());
        }
        return Optional.empty();
    }

    public List<String> perfis() {
        return autenticacao().getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length())).sorted().toList();
    }

    private static Authentication autenticacao() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
