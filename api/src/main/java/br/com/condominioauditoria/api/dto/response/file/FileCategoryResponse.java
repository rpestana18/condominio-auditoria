package br.com.condominioauditoria.api.dto.response.file;

import br.com.condominioauditoria.api.model.enums.FileCategory;

public record FileCategoryResponse(FileCategory code, String label) {
}
