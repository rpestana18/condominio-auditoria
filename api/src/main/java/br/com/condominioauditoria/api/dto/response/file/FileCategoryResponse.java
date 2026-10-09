package br.com.condominioauditoria.api.dto.response.file;

import br.com.condominioauditoria.api.model.enums.FileCategory;
import com.fasterxml.jackson.annotation.JsonProperty;

public record FileCategoryResponse(@JsonProperty("codigo") FileCategory code, @JsonProperty("rotulo") String label) {
}
