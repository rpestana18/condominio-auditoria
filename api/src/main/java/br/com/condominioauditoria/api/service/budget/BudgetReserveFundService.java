package br.com.condominioauditoria.api.service.budget;

import br.com.condominioauditoria.api.model.audit.RuleParameter;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.repository.audit.RuleParameterRepository;
import br.com.condominioauditoria.api.service.audit.rule.ReserveFundCapRule;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Applies the reserve fund cap rule to a confirmed budget. The reserve fund line is the line of the funds group whose
 * printed text (description or account column) mentions "reserva"; the percentage is computed from the values (the
 * line's budgeted amount over the monthly planned amount), not from the "%" column, which is read text.
 */
@Component
public class BudgetReserveFundService {

    public sealed interface Result {
    }

    public record Assessed(BudgetLine line, ReserveFundCapRule.Assessment assessment, RuleParameter parameter)
            implements Result {
    }

    public record NotAssessed(String reason) implements Result {
    }

    private final RuleParameterRepository parameters;

    public BudgetReserveFundService(RuleParameterRepository parameters) {
        this.parameters = parameters;
    }

    public Result assess(Budget budget, BudgetStructure structure) {
        List<BudgetLine> reserveFunds = structure.funds().map(BudgetStructure.Group::lines).orElse(List.of()).stream()
                .filter(BudgetReserveFundService::isReserveFund).toList();
        if (reserveFunds.size() != 1) {
            return new NotAssessed("Regra do teto do fundo de reserva (Conv. 20.1) não avaliada: "
                    + (reserveFunds.isEmpty() ? "a PO não tem linha de fundo de reserva"
                            : "mais de uma linha de fundo de reserva na PO"));
        }
        Optional<RuleParameter> cap = parameters.findValidOn(budget.getCondominiumId(), ReserveFundCapRule.PARAMETER,
                budget.getFiscalYearStart().atDay(1));
        if (cap.isEmpty()) {
            return new NotAssessed("Regra do teto do fundo de reserva (Conv. 20.1) não avaliada: teto não cadastrado"
                    + " para o condomínio em " + budget.getFiscalYearStart());
        }
        BudgetLine reserveFund = reserveFunds.getFirst();
        return ReserveFundCapRule.assess(reserveFund.getBudgeted(), budget.getMonthlyPlanned(), cap.get().getValue())
                .<Result>map(a -> new Assessed(reserveFund, a, cap.get()))
                .orElseGet(() -> new NotAssessed("Regra do teto do fundo de reserva (Conv. 20.1) não avaliada:"
                        + " previsto do mês sem valor"));
    }

    private static boolean isReserveFund(BudgetLine l) {
        return BudgetStructure.normalize(l.getDescription()).contains("reserva")
                || (l.getAccountText() != null && BudgetStructure.normalize(l.getAccountText()).contains("reserva"));
    }
}
