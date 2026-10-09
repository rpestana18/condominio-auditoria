package br.com.condominioauditoria.api.auditoria;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * O evento que levou ao recálculo dos achados (o quê, quem e quando), ex.: "de-para da conta 8888 confirmado" por
 * "admin". Vira o motivo da mudança de estado do achado (RF-03.1.12).
 */
public record GatilhoRecalculo(String descricao, String usuario, Instant em) {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    public GatilhoRecalculo {
        Objects.requireNonNull(descricao, "descricao");
        Objects.requireNonNull(usuario, "usuario");
        Objects.requireNonNull(em, "em");
    }

    /** "de-para da conta 8888 confirmado por admin em 04/10/2026". */
    public String texto() {
        return descricao + " por " + usuario + " em " + DATA.format(em);
    }
}
