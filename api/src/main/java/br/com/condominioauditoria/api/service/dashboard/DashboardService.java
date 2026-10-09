package br.com.condominioauditoria.api.service.dashboard;

import br.com.condominioauditoria.api.dto.response.dashboard.DashboardResponse;
import br.com.condominioauditoria.api.dto.response.dashboard.ExpenseResponse;
import br.com.condominioauditoria.api.dto.response.dashboard.FundPeriodResponse;
import br.com.condominioauditoria.api.dto.response.dashboard.OperatingFundResponse;
import br.com.condominioauditoria.api.mapper.DashboardMapper;
import br.com.condominioauditoria.api.model.accounting.Fund;
import br.com.condominioauditoria.api.model.accounting.FundBalance;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.model.enums.FileStatus;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.accounting.FundBalanceRepository;
import br.com.condominioauditoria.api.repository.accounting.FundRepository;
import br.com.condominioauditoria.api.repository.accounting.LedgerEntryRepository;
import br.com.condominioauditoria.api.repository.accounting.TotalsCheckRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Numbers of the home screen, read from what is already saved (nothing is recalculated on each view). Source: the most
 * recent cash flow. Used by the REST API (screen) and by gRPC (mcp): the number is the same in both. The caller checks
 * access to the condominium first.
 */
@Service
public class DashboardService {

    private static final String CASH_FLOW_PARSER = "fluxo-caixa-protest";

    private final SourceFileRepository files;
    private final FundBalanceRepository balances;
    private final FundRepository funds;
    private final LedgerEntryRepository entries;
    private final TotalsCheckRepository totalsChecks;
    private final CondominiumRepository condominiums;

    DashboardService(SourceFileRepository files, FundBalanceRepository balances, FundRepository funds,
            LedgerEntryRepository entries, TotalsCheckRepository totalsChecks, CondominiumRepository condominiums) {
        this.files = files;
        this.condominiums = condominiums;
        this.balances = balances;
        this.funds = funds;
        this.entries = entries;
        this.totalsChecks = totalsChecks;
    }

    /** Empty while no cash flow was read. */
    @Transactional(readOnly = true)
    public Optional<DashboardResponse> latest(UUID condominiumId) {
        return files.findFirstByCondominiumIdAndParserAndStatusInOrderByPeriodEndDescUploadedAtDesc(
                        condominiumId, CASH_FLOW_PARSER, List.of(FileStatus.CONCLUIDO, FileStatus.PRECISA_REVISAO))
                .map(f -> build(condominiumId, f));
    }

    private DashboardResponse build(UUID condominiumId, SourceFile file) {
        Map<UUID, String> fundNames = funds.findByCondominiumId(condominiumId).stream()
                .collect(Collectors.toMap(Fund::getId, Fund::getName));
        List<FundBalance> positions = balances.findByFileId(file.getId());
        List<FundPeriodResponse> byFund = positions.stream()
                .map(b -> DashboardMapper.toFundPeriod(b, fundNames.get(b.getFundId())))
                .sorted(Comparator.comparing(FundPeriodResponse::closingBalance).reversed())
                .toList();
        List<ExpenseResponse> largest = entries
                .findByFileIdAndInterFundTransferFalseOrderByDebitDesc(file.getId(), Limit.of(10)).stream()
                .map(e -> DashboardMapper.toExpense(e, fundNames.get(e.getFundId())))
                .toList();
        long failedChecks = totalsChecks.findByFileIdOrderBySequence(file.getId()).stream()
                .filter(c -> !c.isOk()).count();
        UUID operatingFundId = condominiums.findById(condominiumId).map(Condominium::getOperatingFundId).orElse(null);
        OperatingFundResponse operatingFund = operatingFund(operatingFundId, fundNames.get(operatingFundId), byFund)
                .orElse(null);
        return new DashboardResponse(file.getId(), file.getOriginalName(), file.getPeriodStart(), file.getPeriodEnd(),
                sum(positions, FundBalance::getOpeningBalance), sum(positions, FundBalance::getCredits),
                sum(positions, FundBalance::getDebits), sum(positions, FundBalance::getClosingBalance),
                failedChecks, operatingFund, byFund, largest);
    }

    /**
     * Closing balance of the operating fund on the home screen card (RF-05.1a and RF-05.1b).
     *
     * <p>With a confirmed fund, uses its balance in the report; if the fund is not in the report, the balance is null.
     * Without confirmation, suggests the fund with the most inflows in the month, which is where the regular condo fees
     * land. The suggestion never becomes a number on the screen without the confirmation of the Manager or the Admin.
     */
    static Optional<OperatingFundResponse> operatingFund(UUID confirmedId, String confirmedName,
            List<FundPeriodResponse> funds) {
        if (confirmedId != null) {
            BigDecimal balance = funds.stream().filter(f -> confirmedId.equals(f.fundId())).findFirst()
                    .map(FundPeriodResponse::closingBalance).orElse(null);
            return Optional.of(new OperatingFundResponse(confirmedId, confirmedName, true, balance));
        }
        return funds.stream()
                .filter(f -> f.inflows().signum() > 0)
                .max(Comparator.comparing(FundPeriodResponse::inflows))
                .map(f -> new OperatingFundResponse(f.fundId(), f.fund(), false, f.closingBalance()));
    }

    private static BigDecimal sum(List<FundBalance> list, Function<FundBalance, BigDecimal> field) {
        return list.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
