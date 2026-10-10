package br.com.condominioauditoria.api.dto.response.user;

import br.com.condominioauditoria.api.dto.response.condominium.CondominiumSummaryResponse;
import java.util.List;

public record CurrentUserResponse(
        String username,
        String name,
        List<String> roles,
        List<CondominiumSummaryResponse> condominiums) {
}
