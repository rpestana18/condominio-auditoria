package br.com.condominioauditoria.api.mapper;

import br.com.condominioauditoria.api.dto.response.budget.ReallocationResponse;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.Reallocation;

/** Reallocation entities → reallocation API DTOs (contracts/openapi.yaml). */
public final class ReallocationMapper {

    private ReallocationMapper() {
    }

    /** {@code line} is the target budget line; null when it no longer exists. */
    public static ReallocationResponse toResponse(Reallocation reallocation, BudgetLine line) {
        return new ReallocationResponse(reallocation.getId(), reallocation.getBudgetId(), reallocation.getDate(),
                reallocation.getAccountCode(), reallocation.getAccountName(), reallocation.getDocument(),
                reallocation.getMemo(), reallocation.getAmount(), reallocation.getFileId(), reallocation.getSha256(),
                reallocation.getPage(), reallocation.getPosition(), reallocation.getBudgetLineId(),
                line == null ? null : line.getEffectiveCode(), line == null ? null : line.getDescription(),
                reallocation.getReallocatedBy(), reallocation.getReallocatedAt(), reallocation.getUndoneBy(),
                reallocation.getUndoneAt(), reallocation.active());
    }
}
