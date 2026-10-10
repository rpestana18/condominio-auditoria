package br.com.condominioauditoria.api.dto.request.dashboard;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ConfirmOperatingFundRequest(@NotNull UUID fundId) {
}
