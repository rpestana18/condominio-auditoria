package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.model.budget.AccountMapping;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.MappingTarget;
import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Copy of the mapping of the budget's previous version as a suggestion (RF-03.1.4; ADR 0004, Decision 4). No account
 * inherits the confirmation. Same line = same effective code, same budget account and same description; the budgeted
 * amount may change. Pure function.
 */
public final class PreviousVersionCopy {

    public sealed interface Result permits Copied, NotCopied {
        String reason();
    }

    /**
     * @param same the target line equals the previous version's (filter "iguais à versão anterior")
     */
    public record Copied(MappingTarget target, boolean same, String reason) implements Result {
    }

    public record NotCopied(String reason) implements Result {
    }

    public static final String SAME = "igual à versão anterior";

    private PreviousVersionCopy() {
    }

    /**
     * @param previous confirmed mapping of the account in the previous version
     *
     * @param previousLines lines of the previous version, by id
     *
     * @param newTargets expense lines of the new version, by effective code
     */
    public static Result copy(AccountMapping previous, Map<UUID, BudgetLine> previousLines,
            Map<String, BudgetLine> newTargets) {
        if (previous.getTargetType() != MappingTargetType.BUDGET_LINE) {
            MappingTarget d = previous.target();
            return new Copied(d, true, SAME + ": " + d.text());
        }
        BudgetLine before = previousLines.get(previous.getBudgetLineId());
        if (before == null) {
            return new NotCopied("a linha de destino da versão anterior não foi encontrada");
        }
        BudgetLine now = newTargets.get(before.getEffectiveCode());
        if (now == null) {
            return new NotCopied("a linha " + before.getEffectiveCode() + " da versão anterior não existe nesta versão");
        }
        if (Objects.equals(before.getAccount(), now.getAccount())
                && Objects.equals(before.getDescription(), now.getDescription())) {
            return new Copied(MappingTarget.line(now), true, SAME + ": " + now.getEffectiveCode() + " "
                    + now.getDescription());
        }
        return new Copied(MappingTarget.line(now), false,
                "linha mudou: antes " + text(before) + "; agora " + text(now));
    }

    private static String text(BudgetLine l) {
        return l.getEffectiveCode() + " " + (l.getAccount() == null ? "" : l.getAccount() + " / ") + l.getDescription();
    }
}
