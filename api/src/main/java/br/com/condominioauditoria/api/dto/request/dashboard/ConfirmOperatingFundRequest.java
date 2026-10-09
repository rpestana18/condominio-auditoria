package br.com.condominioauditoria.api.dto.request.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ConfirmOperatingFundRequest(@JsonProperty("fundoId") @NotNull UUID fundId) {
}
