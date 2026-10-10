package br.com.condominioauditoria.api.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.MappingTarget;
import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import br.com.condominioauditoria.api.service.budget.AccountMappingService;
import br.com.condominioauditoria.api.service.budget.PilotBudget;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-03.1.5 (suggestion sheet): format of the pilot's map; an invalid line is listed and not loaded. */
class AccountMappingSheetTest {

    private final List<BudgetLine> lines = PilotBudget.defaults().savedLines(
            new Budget(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64)));
    private final BudgetStructure structure = BudgetStructure.of(lines);
    private final List<BudgetLine> targets = AccountMappingService.debitTargets(structure);
    private final List<BudgetLine> funds = structure.funds().orElseThrow().lines();

    @Test
    void readsPilotFormat() {
        var reading = AccountMappingSheet.read("""
                conta_fluxo_administradora;linha_PO;status
                1621;1.7.8;sugerido - confirmar
                1324;AJUSTE (estorno);sugerido - confirmar
                1064;REALOCAR (cartão);sugerido - confirmar
                2133;TRANSFERÊNCIA (obras);sugerido - confirmar
                0028;1.6.1;sugerido - confirmar
                """, targets, funds);

        assertThat(reading.rejections()).isEmpty();
        assertThat(reading.items()).extracting(AccountMappingSheet.Item::account)
                .containsExactly("1621", "1324", "1064", "2133", "0028");
        assertThat(reading.items().get(0).target().code()).isEqualTo("1.7.8");
        assertThat(reading.items().get(1).target()).isEqualTo(MappingTarget.special(MappingTargetType.ADJUSTMENT,
                "estorno"));
        assertThat(reading.items().get(2).target()).isEqualTo(MappingTarget.special(MappingTargetType.TO_REALLOCATE,
                "cartão"));
        assertThat(reading.items().get(2).target().text()).isEqualTo("REALOCAR (cartão)");
        assertThat(reading.items().get(3).target().type()).isEqualTo(MappingTargetType.TRANSFER);
    }

    @Test
    void invalidLinesAreListedAndNotLoaded() {
        var reading = AccountMappingSheet.read("""
                1621;1.7.8
                1621;1.3.23
                abc;1.7.8
                1500;9.9.9
                1501;1.9.1
                1502;OUTRA COISA
                1503
                """, targets, funds);

        assertThat(reading.items()).hasSize(1);
        assertThat(reading.rejections()).extracting(AccountMappingSheet.Rejection::line).containsExactly(2, 3, 4, 5, 6,
                7);
        assertThat(reading.rejections().get(0).reason()).contains("repetida");
        assertThat(reading.rejections().get(2).reason()).contains("não existe");
        assertThat(reading.rejections().get(3).reason()).contains("fundo");
    }

    @Test
    void withoutHeaderAndWithBomAndWindowsLineEndings() {
        var reading = AccountMappingSheet.read("﻿1621;1.7.8\r\n1324;AJUSTE\r\n", targets, funds);

        assertThat(reading.rejections()).isEmpty();
        assertThat(reading.items()).hasSize(2);
        assertThat(reading.items().get(1).target().detail()).isNull();
    }
}
