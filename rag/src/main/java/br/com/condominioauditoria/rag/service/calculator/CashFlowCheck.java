package br.com.condominioauditoria.rag.service.calculator;

import static br.com.condominioauditoria.rag.util.BrazilianMoney.format;

import br.com.condominioauditoria.rag.model.TotalsCheck;
import br.com.condominioauditoria.rag.model.cashflow.CashFlow;
import br.com.condominioauditoria.rag.model.cashflow.FundPosition;
import br.com.condominioauditoria.rag.model.cashflow.FundSection;
import br.com.condominioauditoria.rag.model.cashflow.LedgerEntry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Checks whether the cash flow is consistent with itself. It is the first proof that the PDF was read correctly: if
 * some number was read wrong, some of these sums does not match.
 */
public final class CashFlowCheck {

    private CashFlowCheck() {
    }

    public static List<TotalsCheck> check(CashFlow cashFlow) {
        List<TotalsCheck> result = new ArrayList<>();
        Map<String, FundPosition> position = cashFlow.financialPosition().stream()
                .collect(Collectors.toMap(FundPosition::fund, Function.identity(), (a, b) -> a));

        result.add(runningBalance(cashFlow));
        result.add(totalsByFund(cashFlow));
        result.add(closingBalanceByFund(cashFlow, position));
        result.add(positionSum(cashFlow));
        return result;
    }

    /** Opening balance + credit − debit must give the printed balance on each line. */
    private static TotalsCheck runningBalance(CashFlow cashFlow) {
        List<String> errors = new ArrayList<>();
        for (FundSection section : cashFlow.sections()) {
            BigDecimal balance = section.openingBalance();
            for (LedgerEntry l : section.entries()) {
                balance = balance.add(l.credit()).subtract(l.debit());
                if (balance.compareTo(l.balance()) != 0) {
                    errors.add("%s, pág. %d, %s: calculado %s, impresso %s".formatted(
                            section.fund(), l.page(), l.date(), format(balance), format(l.balance())));
                    balance = l.balance();
                }
            }
        }
        return new TotalsCheck("SALDO_CORRENTE", "Saldo linha a linha em todos os fundos", errors.isEmpty(),
                errors.isEmpty() ? cashFlow.entryCount() + " lançamentos conferidos" : String.join("; ", errors));
    }

    /** Sum of the entries of each fund = the report's TOTAIS line. */
    private static TotalsCheck totalsByFund(CashFlow cashFlow) {
        List<String> errors = new ArrayList<>();
        for (FundSection section : cashFlow.sections()) {
            BigDecimal credits = sum(section.entries(), LedgerEntry::credit);
            BigDecimal debits = sum(section.entries(), LedgerEntry::debit);
            if (credits.compareTo(section.reportedCreditTotal()) != 0 || debits.compareTo(section.reportedDebitTotal()) != 0) {
                errors.add("%s: soma %s / %s, TOTAIS %s / %s".formatted(section.fund(), format(credits),
                        format(debits), format(section.reportedCreditTotal()),
                        format(section.reportedDebitTotal())));
            }
        }
        return new TotalsCheck("TOTAIS_FUNDO", "Soma dos lançamentos = linha TOTAIS de cada fundo", errors.isEmpty(),
                errors.isEmpty() ? cashFlow.sections().size() + " fundos conferidos" : String.join("; ", errors));
    }

    /** Opening balance + credits − debits of each section = closing balance in Posição Financeira. */
    private static TotalsCheck closingBalanceByFund(CashFlow cashFlow, Map<String, FundPosition> position) {
        List<String> errors = new ArrayList<>();
        for (FundSection section : cashFlow.sections()) {
            FundPosition p = position.get(section.fund());
            if (p == null) {
                errors.add(section.fund() + ": não aparece na Posição Financeira");
                continue;
            }
            BigDecimal calculated = section.openingBalance()
                    .add(sum(section.entries(), LedgerEntry::credit))
                    .subtract(sum(section.entries(), LedgerEntry::debit));
            if (calculated.compareTo(p.closingBalance()) != 0 || section.openingBalance().compareTo(p.openingBalance()) != 0) {
                errors.add("%s: calculado %s, Posição Financeira %s".formatted(section.fund(), format(calculated),
                        format(p.closingBalance())));
            }
        }
        return new TotalsCheck("SALDO_FINAL_FUNDO", "Saldo final de cada fundo = Posição Financeira", errors.isEmpty(),
                errors.isEmpty() ? cashFlow.sections().size() + " fundos conferidos" : String.join("; ", errors));
    }

    /** Sum of the Posição Financeira lines = TOTAL line. */
    private static TotalsCheck positionSum(CashFlow cashFlow) {
        FundPosition total = cashFlow.positionTotal();
        List<FundPosition> lines = cashFlow.financialPosition();
        boolean ok = total != null
                && sum(lines, FundPosition::openingBalance).compareTo(total.openingBalance()) == 0
                && sum(lines, FundPosition::credits).compareTo(total.credits()) == 0
                && sum(lines, FundPosition::debits).compareTo(total.debits()) == 0
                && sum(lines, FundPosition::closingBalance).compareTo(total.closingBalance()) == 0;
        String detail = total == null ? "Linha TOTAL não encontrada"
                : "Saldo anterior %s + créditos %s − débitos %s = %s".formatted(format(total.openingBalance()),
                        format(total.credits()), format(total.debits()), format(total.closingBalance()));
        return new TotalsCheck("TOTAL_POSICAO", "Soma dos fundos = TOTAL da Posição Financeira", ok, detail);
    }

    private static <T> BigDecimal sum(List<T> items, Function<T, BigDecimal> field) {
        return items.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
