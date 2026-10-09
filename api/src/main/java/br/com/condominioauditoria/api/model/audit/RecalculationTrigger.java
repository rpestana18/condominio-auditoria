package br.com.condominioauditoria.api.model.audit;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * The event that led to recalculating the findings (what, who and when), e.g. "de-para da conta 8888 confirmado" by
 * "admin". It becomes the reason for the finding's status change (RF-03.1.12).
 */
public record RecalculationTrigger(String description, String username, Instant occurredAt) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    public RecalculationTrigger {
        Objects.requireNonNull(description, "descricao");
        Objects.requireNonNull(username, "usuario");
        Objects.requireNonNull(occurredAt, "em");
    }

    /** "de-para da conta 8888 confirmado por admin em 04/10/2026". */
    public String text() {
        return description + " por " + username + " em " + DATE.format(occurredAt);
    }
}
