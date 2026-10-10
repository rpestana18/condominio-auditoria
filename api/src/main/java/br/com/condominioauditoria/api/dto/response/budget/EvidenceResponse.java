package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Entry that makes up a number (RF-03.1.12), with source file, page and hash. */
public record EvidenceResponse(
        UUID entryId,
        LocalDate date,
        String account,
        String accountName,
        String memo,
        String supplier,
        String document,
        BigDecimal amount,
        String fund,
        UUID fileId,
        String fileName,
        String sha256,
        int page,
        int position,
        String reallocation,
        UUID reallocationId) {
}
