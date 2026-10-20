package br.com.condominioauditoria.api.dto.response.accounting;

import java.util.List;
import java.util.UUID;

/** A fund and its months. {@code budgetLineId}/{@code budgetLineCode}: the 1.9 line linked to it, when there is one. */
public record FundMovementResponse(
        UUID fundId,
        String name,
        boolean operatingFund,
        UUID budgetLineId,
        String budgetLineCode,
        List<FundMonthMovementResponse> months) {
}
