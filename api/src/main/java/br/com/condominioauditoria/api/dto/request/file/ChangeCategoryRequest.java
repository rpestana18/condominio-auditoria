package br.com.condominioauditoria.api.dto.request.file;

import br.com.condominioauditoria.api.model.enums.FileCategory;

/** Category change request (RF-01.7). */
public record ChangeCategoryRequest(FileCategory category) {
}
