package br.com.condominioauditoria.api.mapper;

import br.com.condominioauditoria.api.dto.response.file.FundBalanceResponse;
import br.com.condominioauditoria.api.dto.response.file.IndexingResponse;
import br.com.condominioauditoria.api.dto.response.file.SourceFileResponse;
import br.com.condominioauditoria.api.dto.response.file.TotalsCheckResponse;
import br.com.condominioauditoria.api.model.accounting.FundBalance;
import br.com.condominioauditoria.api.model.accounting.TotalsCheck;
import br.com.condominioauditoria.api.model.file.SourceFile;

/** Converts uploaded files and what was read from them into the responses of the Files screen. */
public final class SourceFileMapper {

    private SourceFileMapper() {
    }

    public static SourceFileResponse toResponse(SourceFile file) {
        return new SourceFileResponse(file.getId(), file.getCategory(), file.getCategory().label(),
                file.getOriginalName(), file.getSizeBytes(), file.getStatus(), file.getMessage(), file.getPeriodStart(),
                file.getPeriodEnd(), file.getEntryCount(), file.getUploadedBy(), file.getUploadedAt(),
                file.getProcessedAt(), toIndexingResponse(file));
    }

    /** Null while the file was never sent to the index. */
    public static IndexingResponse toIndexingResponse(SourceFile file) {
        return file.getIndexingStatus() == null ? null
                : new IndexingResponse(file.getIndexingStatus(), file.getIndexingReason(), file.getIndexingPages(),
                        file.getIndexingChunks());
    }

    public static TotalsCheckResponse toResponse(TotalsCheck check) {
        return new TotalsCheckResponse(check.getCode(), check.getDescription(), check.isOk(), check.getDetail());
    }

    public static FundBalanceResponse toResponse(FundBalance balance, String fundName) {
        return new FundBalanceResponse(fundName, balance.getOpeningBalance(), balance.getCredits(), balance.getDebits(),
                balance.getClosingBalance());
    }
}
