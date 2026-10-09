package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.model.budget.AccountMapping;
import br.com.condominioauditoria.api.model.budget.MappingTarget;
import br.com.condominioauditoria.api.model.enums.AccountMappingStatus;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * The mapping that counts in the numbers (RF-03.1.4 and RF-03.1.5): only CONFIRMADO. Suggested and rejected link the
 * account to no target, and its entries go to "sem linha da PO" (premise 3: never silently added to another line). Pure
 * function.
 */
public final class EffectiveAccountMapping {

    private EffectiveAccountMapping() {
    }

    /** Cash flow account → confirmed target. */
    public static Map<String, MappingTarget> confirmed(Collection<AccountMapping> mappings) {
        Map<String, MappingTarget> m = new TreeMap<>();
        mappings.stream().filter(d -> d.getStatus() == AccountMappingStatus.CONFIRMADO)
                .forEach(d -> m.put(d.getAccountCode(), d.target()));
        return m;
    }
}
