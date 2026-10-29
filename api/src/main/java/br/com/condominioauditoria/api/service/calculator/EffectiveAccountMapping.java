package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.model.budget.AccountMapping;
import br.com.condominioauditoria.api.model.budget.MappingTarget;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * The mapping that counts in the numbers (Q54, 29/10/2026; replaces "only CONFIRMED" of RF-03.1.4 and RF-03.1.5): the
 * system's automatic link and the Admin's confirmation, low confidence included. An account without a link, or that the
 * Admin decided has no line (REJECTED), goes to "sem linha da PO" (premise 3: never silently added to another line).
 * Pure function.
 */
public final class EffectiveAccountMapping {

    private EffectiveAccountMapping() {
    }

    /** Cash flow account → target that counts (automatic or confirmed). */
    public static Map<String, MappingTarget> effective(Collection<AccountMapping> mappings) {
        Map<String, MappingTarget> m = new TreeMap<>();
        mappings.stream().filter(d -> d.getStatus() != null && d.getStatus().effective())
                .forEach(d -> m.put(d.getAccountCode(), d.target()));
        return m;
    }
}
