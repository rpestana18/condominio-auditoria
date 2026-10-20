package br.com.condominioauditoria.api.dto.request.file;

import java.time.LocalDate;

/** Manual mark (Manager or Admin): {@code partial} true = partial (until the given date or the upload date), false = complete, null = back to the automatic rule. */
public record PartialMonthRequest(
        Boolean partial,
        LocalDate until,
        String reason) {
}
