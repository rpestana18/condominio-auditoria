package br.com.condominioauditoria.api.controller.feature;

import br.com.condominioauditoria.api.dto.request.feature.ChangeFeatureRequest;
import br.com.condominioauditoria.api.dto.response.feature.ActivePeriodResponse;
import br.com.condominioauditoria.api.dto.response.feature.CondominiumContextResponse;
import br.com.condominioauditoria.api.dto.response.feature.FeatureEventResponse;
import br.com.condominioauditoria.api.dto.response.feature.FeatureResponse;
import br.com.condominioauditoria.api.dto.response.feature.UsageExportResponse;
import br.com.condominioauditoria.api.dto.response.feature.UsageResponse;
import br.com.condominioauditoria.api.mapper.FeatureMapper;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.condominium.CondominiumService;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import br.com.condominioauditoria.api.service.usage.UsageReportService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contractable features and usage per condominium (RF-10, RF-09.7). Permissions (§4 and RF-10.2):
 * <ul>
 * <li>context and feature list: any role with access to the condominium, read only;</li>
 * <li>enable/disable, trail, active periods, usage and export: ADMIN only.</li>
 * </ul>
 * In the MVP the ADMIN is the platform administrator ("everything, in every condominium") and plays the Super-admin
 * of RF-10.2; usage also stays with them until the per-condominium Admin exists (Q17).
 */
@RestController
@RequestMapping("/api/condominios/{condominiumId}")
class FeatureController {

    private final FeatureService features;
    private final UsageReportService usageReports;
    private final CondominiumService condominiums;
    private final CondominiumAccess access;

    FeatureController(FeatureService features, UsageReportService usageReports, CondominiumService condominiums,
            CondominiumAccess access) {
        this.features = features;
        this.usageReports = usageReports;
        this.condominiums = condominiums;
        this.access = access;
    }

    /** What the screen needs to build the menu (RF-10.2, RF-10.3, RF-04.16). */
    @GetMapping("/contexto")
    CondominiumContextResponse context(@PathVariable UUID condominiumId) {
        access.require(condominiumId);
        return condominiums.context(condominiumId);
    }

    @GetMapping("/modulos")
    List<FeatureResponse> list(@PathVariable UUID condominiumId) {
        access.require(condominiumId);
        return features.states(condominiumId).stream().map(FeatureMapper::toResponse).toList();
    }

    @PutMapping("/modulos/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    FeatureResponse change(@PathVariable UUID condominiumId, @PathVariable String code,
            @Valid @RequestBody ChangeFeatureRequest request) {
        access.require(condominiumId);
        condominiums.name(condominiumId);
        return FeatureMapper.toResponse(features.change(condominiumId, code, request.enabled(), request.reason(),
                access.username()));
    }

    @GetMapping("/modulos/{code}/eventos")
    @PreAuthorize("hasRole('ADMIN')")
    List<FeatureEventResponse> events(@PathVariable UUID condominiumId, @PathVariable String code) {
        access.require(condominiumId);
        return features.events(condominiumId, code).stream().map(FeatureMapper::toResponse).toList();
    }

    @GetMapping("/modulos/{code}/periodos")
    @PreAuthorize("hasRole('ADMIN')")
    List<ActivePeriodResponse> periods(@PathVariable UUID condominiumId, @PathVariable String code) {
        access.require(condominiumId);
        return features.periods(condominiumId, code).stream().map(FeatureMapper::toResponse).toList();
    }

    @GetMapping("/uso")
    @PreAuthorize("hasRole('ADMIN')")
    UsageResponse usage(@PathVariable UUID condominiumId,
            @RequestParam("inicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam("fim") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        access.require(condominiumId);
        return usageReports.usage(condominiumId, start, end, access.bearerToken());
    }

    /** Active periods and usage by month in Excel, with the estimated cost in US$ (RF-10.6, RF-09.7). */
    @GetMapping("/uso/exportacao")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<byte[]> export(@PathVariable UUID condominiumId,
            @RequestParam("inicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam("fim") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        access.require(condominiumId);
        UsageExportResponse export = usageReports.export(condominiumId, start, end, access.bearerToken());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(export.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.fileName()).build().toString())
                .body(export.content());
    }
}
