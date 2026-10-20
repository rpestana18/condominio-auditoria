package br.com.condominioauditoria.api.mapper;

import br.com.condominioauditoria.api.dto.response.file.PartialMonthEventResponse;
import br.com.condominioauditoria.api.dto.response.file.PartialMonthResponse;
import br.com.condominioauditoria.api.model.file.PartialMonthEvent;
import br.com.condominioauditoria.api.model.file.SourceFile;
import java.util.UUID;

/** Partial month of the cash flows (RF-13.10) → API DTOs (contracts/openapi.yaml). */
public final class PartialMonthMapper {

    private PartialMonthMapper() {
    }

    public static PartialMonthResponse toResponse(SourceFile f, UUID supersededById) {
        return new PartialMonthResponse(f.getId(), f.getOriginalName(), f.getPeriodStart(), f.getPeriodEnd(),
                f.getUploadedAt(), f.getUploadedBy(), f.isPartial(), f.getPartialOverride() == null,
                f.automaticallyPartial(), f.effectivePartialUntil(), f.getPartialReason(), f.getPartialChangedBy(),
                f.getPartialChangedAt(), supersededById);
    }

    public static PartialMonthEventResponse toResponse(PartialMonthEvent e) {
        return new PartialMonthEventResponse(e.getId(), e.getFileId(), e.getAction(), e.isPreviousPartial(),
                e.isNewPartial(), e.getPartialUntil(), e.getSupersededById(), e.getUsername(), e.getOccurredAt(),
                e.getReason());
    }
}
