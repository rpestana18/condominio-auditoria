package br.com.condominioauditoria.api.dto.response.accounting;

import java.util.UUID;

public record FundResponse(UUID id, String name, boolean operating) {
}
