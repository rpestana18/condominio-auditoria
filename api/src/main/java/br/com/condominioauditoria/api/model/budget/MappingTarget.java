package br.com.condominioauditoria.api.model.budget;

import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import java.util.Objects;
import java.util.UUID;

/**
 * Target of a cash flow account: the budget line (by internal id, never by the budget's account) or a special target
 * with the text read, e.g. "AJUSTE (estorno)".
 *
 * @param budgetLineId budget line, only with {@link MappingTargetType#BUDGET_LINE}
 *
 * @param code effective code of the line, only for the text (matching is by id)
 *
 * @param description description of the line, only for the text
 *
 * @param detail text in parentheses of the special target, or null
 */
public record MappingTarget(MappingTargetType type, UUID budgetLineId, String code, String description, String detail) {

    public MappingTarget {
        Objects.requireNonNull(type, "tipo");
        if ((type == MappingTargetType.BUDGET_LINE) != (budgetLineId != null)) {
            throw new IllegalArgumentException("Destino BUDGET_LINE exige a linha da PO; os demais não têm linha");
        }
        detail = detail == null || detail.isBlank() ? null : detail.trim();
    }

    public static MappingTarget line(BudgetLine line) {
        return new MappingTarget(MappingTargetType.BUDGET_LINE, line.getId(), line.getEffectiveCode(),
                line.getDescription(), null);
    }

    public static MappingTarget special(MappingTargetType type, String detail) {
        return new MappingTarget(type, null, null, null, detail);
    }

    /** Same target (the line text does not count, only the id). */
    public boolean sameAs(MappingTarget other) {
        return other != null && type == other.type && Objects.equals(budgetLineId, other.budgetLineId)
                && Objects.equals(detail, other.detail);
    }

    /** As shown on screen and in the trail, in the sheet format: "1.7.8 Material hidráulico", "AJUSTE (estorno)". */
    public String text() {
        if (type == MappingTargetType.BUDGET_LINE) {
            return (code == null ? "linha " + budgetLineId : code) + (description == null ? "" : " " + description);
        }
        String label = switch (type) {
            case ADJUSTMENT -> "AJUSTE";
            case TO_REALLOCATE -> "REALOCAR";
            case TRANSFER -> "TRANSFERENCIA";
            case BUDGET_LINE -> throw new IllegalStateException();
        };
        return detail == null ? label : label + " (" + detail + ")";
    }
}
