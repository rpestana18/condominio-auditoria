package br.com.condominioauditoria.api.controller.budget;

import br.com.condominioauditoria.api.dto.request.budget.AccountMappingBatchRequest;
import br.com.condominioauditoria.api.dto.request.budget.MappingTargetRequest;
import br.com.condominioauditoria.api.dto.response.budget.AccountMappingBatchResponse;
import br.com.condominioauditoria.api.dto.response.budget.AccountMappingEventResponse;
import br.com.condominioauditoria.api.dto.response.budget.AccountMappingResponse;
import br.com.condominioauditoria.api.dto.response.budget.AccountMappingSheetResponse;
import br.com.condominioauditoria.api.dto.response.budget.AccountMappingSuggestionsResponse;
import br.com.condominioauditoria.api.dto.response.budget.AccountMappingsResponse;
import br.com.condominioauditoria.api.model.enums.AccountMappingFilter;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.budget.AccountMappingService;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Account mapping of the cash flow accounts (RF-03.1.4, RF-03.1.5 and RF-03.1.13). Reading: every role of the
 * condominium. Creating, changing, confirming, rejecting, suggesting and uploading the sheet: only the Admin (Gestor
 * and Usuário get 403).
 */
@RestController
@RequestMapping("/api/condominiums/{condominiumId}/budgets/{budgetId}/account-mappings")
public class AccountMappingController {

    /** The pilot's sheet is 3 KB; 1 MB leaves room for thousands of accounts. */
    static final long SHEET_MAX_SIZE = 1024 * 1024;

    private final CondominiumAccess access;
    private final AccountMappingService service;

    public AccountMappingController(CondominiumAccess access, AccountMappingService service) {
        this.access = access;
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    public AccountMappingsResponse list(@PathVariable UUID condominiumId, @PathVariable UUID budgetId,
            @RequestParam(name = "filter", required = false) AccountMappingFilter filter) {
        access.require(condominiumId);
        return service.list(condominiumId, budgetId, filter);
    }

    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    public List<AccountMappingEventResponse> events(@PathVariable UUID condominiumId, @PathVariable UUID budgetId) {
        access.require(condominiumId);
        return service.events(condominiumId, budgetId);
    }

    @PutMapping("/{account}")
    @PreAuthorize("hasRole('ADMIN')")
    public AccountMappingResponse setTarget(@PathVariable UUID condominiumId, @PathVariable UUID budgetId,
            @PathVariable String account,
            @RequestBody MappingTargetRequest request) {
        access.require(condominiumId);
        return service.setTarget(condominiumId, budgetId, account, request, access.username());
    }

    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public AccountMappingBatchResponse batch(@PathVariable UUID condominiumId, @PathVariable UUID budgetId,
            @RequestBody AccountMappingBatchRequest request) {
        access.require(condominiumId);
        return service.batch(condominiumId, budgetId, request, access.username());
    }

    @PostMapping("/suggestions")
    @PreAuthorize("hasRole('ADMIN')")
    public AccountMappingSuggestionsResponse suggest(@PathVariable UUID condominiumId, @PathVariable UUID budgetId) {
        access.require(condominiumId);
        return service.suggest(condominiumId, budgetId, access.username());
    }

    @PostMapping(path = "/sheet", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public AccountMappingSheetResponse uploadSheet(@PathVariable UUID condominiumId, @PathVariable UUID budgetId,
            @RequestPart("file") MultipartFile file) throws IOException {
        access.require(condominiumId);
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "A planilha está vazia");
        }
        if (file.getSize() > SHEET_MAX_SIZE) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "A planilha passa de 1 MB");
        }
        return service.loadSheet(condominiumId, budgetId, file.getOriginalFilename(), text(file.getBytes()),
                access.username());
    }

    /** UTF-8 (default); when it is not valid UTF-8, Windows-1252, as Excel in Portuguese saves the CSV. */
    static String text(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, java.nio.charset.Charset.forName("windows-1252"));
        }
    }
}
