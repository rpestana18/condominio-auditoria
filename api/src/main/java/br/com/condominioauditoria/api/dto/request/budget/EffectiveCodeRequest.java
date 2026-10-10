package br.com.condominioauditoria.api.dto.request.budget;

import java.util.UUID;

/** Effective code chosen by the Admin for a line whose printed code repeats (RF-03.1.2). */
public record EffectiveCodeRequest(UUID lineId, String code) {
}
