package br.com.condominioauditoria.api.mapper;

import br.com.condominioauditoria.api.dto.response.dashboard.ExpenseResponse;
import br.com.condominioauditoria.api.dto.response.dashboard.FundPeriodResponse;
import br.com.condominioauditoria.api.model.accounting.FundBalance;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;

public final class DashboardMapper {

    private DashboardMapper() {
    }

    /** The fund's line in the period: inflows are the credits, outflows the debits. */
    public static FundPeriodResponse toFundPeriod(FundBalance balance, String fundName) {
        return new FundPeriodResponse(balance.getFundId(), fundName, balance.getOpeningBalance(), balance.getCredits(),
                balance.getDebits(), balance.getCredits().subtract(balance.getDebits()), balance.getClosingBalance());
    }

    /** An outflow of the period: the account shows its code before the name when the cash flow prints one. */
    public static ExpenseResponse toExpense(LedgerEntry entry, String fundName) {
        String account = entry.getAccountCode() == null ? entry.getAccountName()
                : entry.getAccountCode() + " " + entry.getAccountName();
        return new ExpenseResponse(entry.getDate(), fundName, account, entry.getMemo(), entry.getDebit(),
                entry.getPage());
    }
}
