package br.com.condominioauditoria.api.dto.request.file;

import br.com.condominioauditoria.api.model.enums.FileCategory;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Category change request (RF-01.7). */
public record ChangeCategoryRequest(@JsonProperty("categoria") FileCategory category) {
}
