package br.com.condominioauditoria.api.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Isolation between condominiums: the token carries the "condominios" list the user has access to. Admin sees all. */
@Component
public class CondominiumAccess {

    public void require(UUID condominiumId) {
        if (!canAccess(condominiumId)) {
            throw new AccessDeniedException("Sem acesso a este condomínio");
        }
    }

    public boolean canAccess(UUID condominiumId) {
        return isAdmin() || tokenCondominiums().contains(condominiumId.toString());
    }

    public boolean isAdmin() {
        return authentication().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    public List<String> tokenCondominiums() {
        if (authentication().getPrincipal() instanceof Jwt jwt) {
            List<String> list = jwt.getClaimAsStringList("condominios");
            return list == null ? List.of() : list;
        }
        return List.of();
    }

    public String username() {
        return authentication().getName();
    }

    public String fullName() {
        if (authentication().getPrincipal() instanceof Jwt jwt && jwt.hasClaim("name")) {
            return jwt.getClaimAsString("name");
        }
        return username();
    }

    /**
     * Token of the calling user, ready for the "authorization" header ("Bearer ..."). Used to pass the same user on to
     * the rag, which validates the token again.
     */
    public Optional<String> bearerToken() {
        Authentication authentication = authentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.of("Bearer " + jwt.getTokenValue());
        }
        return Optional.empty();
    }

    public List<String> roles() {
        return authentication().getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length())).sorted().toList();
    }

    private static Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
