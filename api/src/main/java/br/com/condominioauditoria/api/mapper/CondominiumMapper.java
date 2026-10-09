package br.com.condominioauditoria.api.mapper;

import br.com.condominioauditoria.api.dto.response.condominium.CondominiumSummaryResponse;
import br.com.condominioauditoria.api.model.condominium.Condominium;

public final class CondominiumMapper {

    private CondominiumMapper() {
    }

    public static CondominiumSummaryResponse toSummary(Condominium condominium) {
        return new CondominiumSummaryResponse(condominium.getId(), condominium.getName());
    }
}
