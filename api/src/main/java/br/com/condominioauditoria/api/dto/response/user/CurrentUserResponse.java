package br.com.condominioauditoria.api.dto.response.user;

import br.com.condominioauditoria.api.dto.response.condominium.CondominiumSummaryResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CurrentUserResponse(
        @JsonProperty("usuario") String username,
        @JsonProperty("nome") String name,
        @JsonProperty("perfis") List<String> roles,
        @JsonProperty("condominios") List<CondominiumSummaryResponse> condominiums) {
}
