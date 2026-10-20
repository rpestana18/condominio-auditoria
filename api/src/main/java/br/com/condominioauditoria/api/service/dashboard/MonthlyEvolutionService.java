package br.com.condominioauditoria.api.service.dashboard;

import br.com.condominioauditoria.api.dto.response.dashboard.MonthlyEvolutionResponse;
import br.com.condominioauditoria.api.model.accounting.Fund;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.FileStatus;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.accounting.FundRepository;
import br.com.condominioauditoria.api.repository.accounting.LedgerEntryRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.CashFlowFile;
import br.com.condominioauditoria.api.service.calculator.MonthlyEvolution;
import br.com.condominioauditoria.api.service.calculator.PartialCashFlows;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Month-by-month evolution of the home screen (RF-12), calculated on each query from what is saved: the cash flows
 * read, their entries, the confirmed operating fund (RF-05.1b) and the confirmed utility accounts (RF-12.1). The caller
 * checks access to the condominium first.
 */
@Service
public class MonthlyEvolutionService {

    private static final EnumSet<FileStatus> READ = EnumSet.of(FileStatus.COMPLETED, FileStatus.NEEDS_REVIEW);

    private final SourceFileRepository files;
    private final LedgerEntryRepository entries;
    private final FundRepository funds;
    private final CondominiumRepository condominiums;
    private final UtilityAccountService utilityAccounts;

    MonthlyEvolutionService(SourceFileRepository files, LedgerEntryRepository entries, FundRepository funds,
            CondominiumRepository condominiums, UtilityAccountService utilityAccounts) {
        this.files = files;
        this.entries = entries;
        this.funds = funds;
        this.condominiums = condominiums;
        this.utilityAccounts = utilityAccounts;
    }

    /**
     * The 12 months up to {@code until}; with no month given, up to the last month with a cash flow. Empty while no
     * cash flow was read.
     */
    @Transactional(readOnly = true)
    public Optional<MonthlyEvolutionResponse> evolution(UUID condominiumId, YearMonth until) {
        var read = files.findByCondominiumIdAndCategoryAndStatusIn(condominiumId, FileCategory.TRIAL_BALANCE, READ)
                .stream().filter(f -> f.getPeriodStart() != null && f.getPeriodEnd() != null).toList();
        Map<UUID, java.time.LocalDate> partial = new java.util.HashMap<>();
        read.stream().filter(SourceFile::isPartial).forEach(f -> partial.put(f.getId(), f.effectivePartialUntil()));
        // RF-13.10: a partial cash flow replaced by the complete one of the same month is left out
        List<CashFlowFile> cashFlows = PartialCashFlows.select(read.stream()
                .map(f -> new CashFlowFile(f.getId(), f.getOriginalName(), f.getSha256(), f.getPeriodStart(),
                        f.getPeriodEnd(), f.getUploadedAt(), f.getUploadedBy()))
                .toList(), partial).effective();
        if (cashFlows.isEmpty()) {
            return Optional.empty();
        }
        YearMonth last = until != null ? until
                : cashFlows.stream().map(f -> YearMonth.from(f.periodEnd())).max(YearMonth::compareTo).orElseThrow();
        Map<UUID, String> fundNames = funds.findByCondominiumId(condominiumId).stream()
                .collect(Collectors.toMap(Fund::getId, Fund::getName));
        UUID operatingFundId = condominiums.findById(condominiumId).map(Condominium::getOperatingFundId).orElse(null);
        var used = entries.findByFileIdInAndDateBetween(cashFlows.stream().map(CashFlowFile::fileId).toList(),
                MonthlyEvolution.firstNeeded(last).atDay(1), last.atEndOfMonth());
        return Optional.of(MonthlyEvolution.calculate(new MonthlyEvolution.Input(last, cashFlows, used,
                operatingFundId, fundNames.get(operatingFundId), fundNames, utilityAccounts.confirmed(condominiumId),
                utilityAccounts.pending(condominiumId))));
    }
}
