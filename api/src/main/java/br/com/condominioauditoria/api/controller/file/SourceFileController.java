package br.com.condominioauditoria.api.controller.file;

import br.com.condominioauditoria.api.dto.request.file.ChangeCategoryRequest;
import br.com.condominioauditoria.api.dto.response.file.FileCategoryResponse;
import br.com.condominioauditoria.api.dto.response.file.FileContentResponse;
import br.com.condominioauditoria.api.dto.response.file.SourceFileDetailResponse;
import br.com.condominioauditoria.api.dto.response.file.SourceFileResponse;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.file.SourceFileService;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Files screen: upload by category, list, detail, download, reprocess and category change. */
@RestController
@RequestMapping("/api")
class SourceFileController {

    private final SourceFileService service;
    private final CondominiumAccess access;

    SourceFileController(SourceFileService service, CondominiumAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping("/categories")
    List<FileCategoryResponse> categories() {
        return Arrays.stream(FileCategory.values()).map(c -> new FileCategoryResponse(c, c.label())).toList();
    }

    /** Newest first, optionally filtered by category. */
    @GetMapping("/condominiums/{condominiumId}/files")
    List<SourceFileResponse> list(@PathVariable UUID condominiumId,
            @RequestParam(name = "category", required = false) FileCategory category) {
        access.require(condominiumId);
        return service.list(condominiumId, category);
    }

    /** For the discreet "latest file" indicator in the corner of the screen. */
    @GetMapping("/condominiums/{condominiumId}/files/latest")
    ResponseEntity<SourceFileResponse> latest(@PathVariable UUID condominiumId) {
        access.require(condominiumId);
        return service.latest(condominiumId).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/condominiums/{condominiumId}/files/{id}")
    SourceFileDetailResponse detail(@PathVariable UUID condominiumId, @PathVariable UUID id) {
        access.require(condominiumId);
        return service.detail(condominiumId, id);
    }

    /** Downloads the original, exactly as it was uploaded. */
    @GetMapping("/condominiums/{condominiumId}/files/{id}/content")
    ResponseEntity<InputStreamResource> content(@PathVariable UUID condominiumId, @PathVariable UUID id)
            throws IOException {
        access.require(condominiumId);
        FileContentResponse file = service.content(condominiumId, id);
        MediaType type = file.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(file.contentType());
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.originalName()).build().toString())
                .body(new InputStreamResource(file.content()));
    }

    @PostMapping(path = "/condominiums/{condominiumId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    SourceFileResponse upload(@PathVariable UUID condominiumId, @RequestParam("category") FileCategory category,
            @RequestPart("file") MultipartFile file) throws IOException {
        access.require(condominiumId);
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo vazio");
        }
        return service.upload(condominiumId, category, file, access.username());
    }

    @PostMapping("/condominiums/{condominiumId}/files/{id}/reprocess")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    SourceFileResponse reprocess(@PathVariable UUID condominiumId, @PathVariable UUID id) {
        access.require(condominiumId);
        return service.reprocess(condominiumId, id);
    }

    /** Changes the category of a file already uploaded and reprocesses it with the new one (RF-01.7). */
    @PutMapping("/condominiums/{condominiumId}/files/{id}/category")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    SourceFileResponse changeCategory(@PathVariable UUID condominiumId, @PathVariable UUID id,
            @RequestBody ChangeCategoryRequest request) {
        if (request == null || request.category() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a categoria");
        }
        access.require(condominiumId);
        return service.changeCategory(condominiumId, id, request.category(), access.username());
    }
}
