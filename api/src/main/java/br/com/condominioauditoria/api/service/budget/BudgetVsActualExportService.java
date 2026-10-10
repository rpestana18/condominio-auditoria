package br.com.condominioauditoria.api.service.budget;

import br.com.condominioauditoria.api.dto.response.budget.ExportedFileResponse;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.model.enums.ExportFormat;
import br.com.condominioauditoria.api.report.BudgetVsActualExcelReport;
import br.com.condominioauditoria.api.report.BudgetVsActualPdfReport;
import br.com.condominioauditoria.api.report.BudgetVsActualReport;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Budget vs. actual export to PDF or Excel (RF-03.1.14), with the same filters as the screen. Uses the same
 * {@link BudgetVsActualCalculator} as the query: the file's numbers are the JSON's, by construction.
 */
@Service
public class BudgetVsActualExportService {

    private final CondominiumRepository condominiums;
    private final BudgetVsActualQueryService query;
    private final BudgetVsActualPdfReport pdf;
    private final BudgetVsActualExcelReport excel;

    public BudgetVsActualExportService(CondominiumRepository condominiums, BudgetVsActualQueryService query,
            BudgetVsActualPdfReport pdf,
            BudgetVsActualExcelReport excel) {
        this.condominiums = condominiums;
        this.query = query;
        this.pdf = pdf;
        this.excel = excel;
    }

    @Transactional(readOnly = true)
    public ExportedFileResponse export(UUID condominiumId, String period, UUID budgetId, UUID fundId, String formatText,
            String generatedBy) {
        ExportFormat format = format(formatText);
        Condominium condominium = condominiums.findById(condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        var calculation = query.calculate(condominiumId, period, budgetId, fundId);
        String fund = fundId == null ? null : fundId.equals(condominium.getOperatingFundId())
                ? "Condomínio (fundo ordinário: " + query.filterFund(condominiumId, fundId).getName() + ")"
                : query.filterFund(condominiumId, fundId).getName();
        BudgetVsActualReport report = BudgetVsActualReport.build(condominium.getName(), fund, calculation,
                generatedBy, Instant.now());
        byte[] content = format == ExportFormat.PDF ? pdf.generate(report) : excel.generate(report);
        String period = calculation.result().period();
        // The file name is for people: the API code "cumulative" is shown as "acumulado"
        String name = "previsto-realizado-" + ("cumulative".equals(period) ? "acumulado" : period) + "."
                + format.extension();
        return new ExportedFileResponse(name, format.contentType(), content);
    }

    /** The {@code formato} parameter: pdf or xlsx, in any case. */
    private static ExportFormat format(String text) {
        String t = text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
        try {
            return ExportFormat.valueOf(t);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato deve ser pdf ou xlsx: " + text);
        }
    }
}
