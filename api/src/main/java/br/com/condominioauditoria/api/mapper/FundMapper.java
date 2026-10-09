package br.com.condominioauditoria.api.mapper;

import br.com.condominioauditoria.api.dto.response.accounting.FundResponse;
import br.com.condominioauditoria.api.model.accounting.Fund;

public final class FundMapper {

    private FundMapper() {
    }

    public static FundResponse toResponse(Fund fund, boolean operating) {
        return new FundResponse(fund.getId(), fund.getName(), operating);
    }
}
