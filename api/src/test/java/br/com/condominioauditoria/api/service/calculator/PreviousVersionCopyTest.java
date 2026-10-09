package br.com.condominioauditoria.api.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.model.budget.AccountMapping;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.MappingTarget;
import br.com.condominioauditoria.api.model.enums.AccountMappingSource;
import br.com.condominioauditoria.api.model.enums.AccountMappingStatus;
import br.com.condominioauditoria.api.model.enums.BudgetLineType;
import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * RF-03.1.4: a new budget version gets the previous mapping as a suggestion; same line × changed line × missing line.
 */
class PreviousVersionCopyTest {

    private final Budget v1 = new Budget(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64));
    private final Budget v2 = new Budget(v1.getCondominiumId(), UUID.randomUUID(), "b".repeat(64));
    private final BudgetLine manager1 = line(v1, "1.3.20", "1682 - Sindicatura Profissional", "Obm - Sergio Diniz",
            "8000.00");
    private final BudgetLine plumbing1 = line(v1, "1.7.8", "1659 - Material Hidráulico", "Material Hidráulico",
            "1800.00");
    private final BudgetLine painting1 = line(v1, "1.7.9", "1645 - Material de Pintura", "Material de pintura",
            "2300.00");
    private final Map<UUID, BudgetLine> previousLines = Map.of(manager1.getId(), manager1, plumbing1.getId(), plumbing1,
            painting1.getId(), painting1);
    // Version 2: 1.3.20 only changes amount; 1.7.8 changes description; 1.7.9 no longer exists
    private final Map<String, BudgetLine> newLines = java.util.stream.Stream.of(
                    line(v2, "1.3.20", "1682 - Sindicatura Profissional", "Obm - Sergio Diniz", "9000.00"),
                    line(v2, "1.7.8", "1659 - Material Hidráulico", "Material hidráulico e elétrico", "1800.00"))
            .collect(Collectors.toMap(BudgetLine::getEffectiveCode, Function.identity()));

    @Test
    void sameLineEvenWithAnotherValue() {
        var r = PreviousVersionCopy.copy(confirmed("1108", MappingTarget.line(manager1)), previousLines, newLines);

        var c = (PreviousVersionCopy.Copied) r;
        assertThat(c.same()).isTrue();
        assertThat(c.target().budgetLineId()).isEqualTo(newLines.get("1.3.20").getId());
        assertThat(c.reason()).startsWith("igual à versão anterior");
    }

    @Test
    void changedLineComesInWithReason() {
        var r = PreviousVersionCopy.copy(confirmed("1621", MappingTarget.line(plumbing1)), previousLines, newLines);

        var c = (PreviousVersionCopy.Copied) r;
        assertThat(c.same()).isFalse();
        assertThat(c.reason()).startsWith("linha mudou").contains("Material Hidráulico")
                .contains("Material hidráulico e elétrico");
    }

    @Test
    void missingLineHasNoSuggestion() {
        var r = PreviousVersionCopy.copy(confirmed("1088", MappingTarget.line(painting1)), previousLines, newLines);

        assertThat(r).isInstanceOf(PreviousVersionCopy.NotCopied.class);
        assertThat(r.reason()).isEqualTo("a linha 1.7.9 da versão anterior não existe nesta versão");
    }

    @Test
    void specialTargetCarriesOverAsSame() {
        var r = PreviousVersionCopy.copy(confirmed("1324", MappingTarget.special(MappingTargetType.AJUSTE, "estorno")),
                previousLines, newLines);

        var c = (PreviousVersionCopy.Copied) r;
        assertThat(c.same()).isTrue();
        assertThat(c.target().text()).isEqualTo("AJUSTE (estorno)");
    }

    private AccountMapping confirmed(String account, MappingTarget target) {
        return new AccountMapping(v1, account, null, target, AccountMappingStatus.CONFIRMADO,
                AccountMappingSource.ADMIN, null, false, "admin",
                Instant.EPOCH);
    }

    private static BudgetLine line(Budget budget, String code, String account, String description, String amount) {
        return new BudgetLine(budget, 1, 1, BudgetLineType.LINHA, code, account, null, null, description,
                new BigDecimal(amount),
                new BigDecimal(amount), null, null);
    }
}
