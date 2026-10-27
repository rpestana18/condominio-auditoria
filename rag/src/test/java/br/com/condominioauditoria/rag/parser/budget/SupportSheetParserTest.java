package br.com.condominioauditoria.rag.parser.budget;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.rag.model.budget.BudgetLineOrigin;
import br.com.condominioauditoria.rag.model.budget.BudgetLineOrigins;
import br.com.condominioauditoria.rag.model.document.ReadDocument;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Cell;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Sheet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * RF-13.13 and ADR 0009, Decision 3 B: the method of each line deduced from the numbers of the support sheet, with
 * synthetic cells in the pilot's layout (columns found by the header, not by the position).
 */
public class SupportSheetParserTest {

    private final SupportSheetParser parser = new SupportSheetParser();

    @Test
    public void recognizesOnlyAWorkbookWithTheBudgetSheet() {
        assertThat(parser.recognizes(workbook())).isTrue();
        assertThat(parser.recognizes(new ReadDocument("2", "x", null, "xlsx", List.of(),
                List.of(new Sheet("Outra", List.of(text(1, 1, "Qualquer")))), List.of()))).isFalse();
        assertThat(parser.recognizes(new ReadDocument("2", "x", null, "pdf", List.of(), List.of(), List.of())))
                .isFalse();
    }

    @Test
    public void deducesEachMethodFromTheNumbers() {
        BudgetLineOrigins origins = parser.parse(workbook());

        assertThat(origins.sheet()).isEqualTo("PO-26-27");
        assertThat(origins.expensesSheet()).isEqualTo("despesas");
        assertThat(origins.indexSheet()).isEqualTo("indices");
        // The funds group (1.9) is not an expense line
        assertThat(origins.lines()).extracting(BudgetLineOrigin::sequence).containsExactly(1, 2, 3, 4, 5, 6);

        BudgetLineOrigin vigia = origins.lines().get(0);
        assertThat(vigia.printedCode()).isEqualTo("2010-01-03T00:00:00");
        assertThat(vigia.sourceAccounts()).containsExactly("1692");
        assertThat(vigia.method()).isEqualTo("AVERAGE_TIMES_INDEX");
        assertThat(vigia.averageFrom()).isEqualTo("2026-01");
        assertThat(vigia.averageTo()).isEqualTo("2026-04");
        assertThat(vigia.averageAmount()).isEqualByComparingTo("74627.40");
        assertThat(vigia.indexName()).isEqualTo("VIGILANTES RJ");
        assertThat(vigia.indexRate()).isEqualByComparingTo("0.0723");
        assertThat(vigia.finalAmount()).isEqualByComparingTo("80022.96");
        assertThat(vigia.finalAmount().scale()).isEqualTo(2);
        assertThat(vigia.indexNote()).isNull();

        BudgetLineOrigin garden = origins.lines().get(1);
        assertThat(garden.method()).isEqualTo("TYPED");
        assertThat(garden.reason()).isEqualTo("Negociação contratual");

        BudgetLineOrigin phone = origins.lines().get(2);
        assertThat(phone.sourceAccounts()).containsExactly("1591", "1592");
        assertThat(phone.method()).isEqualTo("EQUAL_TO_AVERAGE");
        assertThat(phone.averageFrom()).isEqualTo("2025-08");
        assertThat(phone.averageAmount()).isEqualByComparingTo("208.63");

        BudgetLineOrigin fixed = origins.lines().get(3);
        assertThat(fixed.method()).isEqualTo("NO_AVERAGE");
        assertThat(fixed.averageFrom()).isNull();

        // Same value in two indexes: the one cited in Observações wins, with the note (O4)
        BudgetLineOrigin radios = origins.lines().get(4);
        assertThat(radios.method()).isEqualTo("AVERAGE_TIMES_INDEX");
        assertThat(radios.indexName()).isEqualTo("IGP-M");
        assertThat(radios.indexNote()).isEqualTo("rótulo IGP-M, valor aplicado 4,44% (igual ao IPCA ACUMULADO)");

        // Forecast index (dissídio "(previsão)")
        BudgetLineOrigin staff = origins.lines().get(5);
        assertThat(staff.indexName()).isEqualTo("DISSÍDIO SEEMRJ");
        assertThat(staff.indexForecast()).isTrue();
        assertThat(staff.averageFrom()).isEqualTo("2025-08");

        assertThat(SupportSheetParser.check(origins).getFirst().ok()).isTrue();
        assertThat(SupportSheetParser.check(origins).getFirst().detail()).isEqualTo("6 linhas lidas, 5 com média de conta");
    }

    static ReadDocument workbook() {
        List<Cell> po = new ArrayList<>();
        // Columns out of the pilot's order on purpose: found by the header
        header(po, 2, "Item", "IDENTIFICAÇÃO DESPESA", "DESCRIÇÃO", "ORÇADO 2025/2026", "ORÇADO 2026/2027",
                "REALIZADO 2025/2026 (MÉDIA)", "Observações");
        row(po, 3, "1", "Soma", "TOTAL DAS DESPESAS", "1", "1", "1", null);
        row(po, 4, "1.3", "Subtotal", "SERVIÇOS - CONTRATOS EFETIVOS", "1", "1", "1", null);
        po.add(new Cell(5, 1, "2010-01-03T00:00:00", "date"));
        po.add(text(5, 2, "1692 - Vigia e Portaria"));
        po.add(text(5, 3, "Join Facilities Ltda"));
        po.add(number(5, 4, "106196.03"));
        po.add(number(5, 5, "80022.96102"));
        po.add(number(5, 6, "74627.4"));
        po.add(text(5, 7, "Média jan 26/abr 26 + VIGILANTES RJ"));
        row(po, 6, "1.3.8", "1690 - Jardinagem", "3JF", "13483.2", "18000", "13480", "Negociação contratual");
        row(po, 7, "1.2.1", "1591 - Telefone Fixo + 1592 - Telefone Móvel", "Telefone", "260", "208.63333",
                "208.6333333", null);
        row(po, 8, "1.5.3", "Valor fixo (sem referência)", "Outras aquisições", "700", "0", "700", null);
        row(po, 9, "1.3.9", "1694 - Locação Rádios", "Silradio", "413.6", "431.96384", "413.6",
                "Média jan 26/abr 26 + IGP-M");
        row(po, 10, "1.1.1", "1545 - Verbas Salariais", "Salários", "19621.62", "35934.15316", "33740.98888889",
                "Média ago 25/abr 26 + Dissídio");
        row(po, 11, "1.9", "Fundos", "Fundos do Condomínio", "1", "1", null, null);
        row(po, 12, "1.9.1", "Fundo de Reserva", "Fundo de Reserva", "1", "1", null, null);

        List<Cell> expenses = new ArrayList<>();
        expenses.add(text(1, 1, "DESPESAS"));
        expenses.add(text(1, 2, "MÉDIA"));
        expenses.add(text(1, 3, "MÉDIA AGO/25-ABR/26"));
        expenses.add(text(1, 4, "MÉDIA JAN/26-ABR/26"));
        account(expenses, 2, "1692 - Vigia e Portaria", "1", "70000", "74627.4");
        account(expenses, 3, "1690 - Jardinagem", "1", "13000", "13480");
        account(expenses, 4, "1591 - Telefone Fixo", "1", "100.5", "90");
        account(expenses, 5, "1592 - Telefone Móvel", "1", "108.1333333", "90");
        account(expenses, 6, "1694 - Locação Rádios", "1", "400", "413.6");
        account(expenses, 7, "1545 - Verbas Salariais", "1", "33740.98888889", "30912.05");

        List<Cell> indexes = new ArrayList<>();
        indexes.add(text(1, 1, "TAXA"));
        indexes.add(text(1, 2, "PERCENTUAL"));
        indexes.add(text(2, 1, "IPCA ACUMULADO"));
        indexes.add(number(2, 2, "0.0444"));
        indexes.add(text(3, 1, "DISSÍDIO SEEMRJ"));
        indexes.add(number(3, 2, "0.065"));
        indexes.add(text(3, 3, "(previsão)"));
        indexes.add(text(4, 1, "VIGILANTES RJ"));
        indexes.add(number(4, 2, "0.0723"));
        indexes.add(text(5, 1, "IGP-M"));
        indexes.add(number(5, 2, "0.0444"));
        indexes.add(number(5, 3, "0.0062"));

        return new ReadDocument("2", "leitor", null, "xlsx", List.of(),
                List.of(new Sheet("Capa", List.of(text(1, 1, "PROPOSTA"))), new Sheet("PO-26-27", po),
                        new Sheet("despesas", expenses), new Sheet("indices", indexes)),
                List.of());
    }

    private static void header(List<Cell> cells, int row, String... titles) {
        for (int i = 0; i < titles.length; i++) {
            cells.add(text(row, i + 1, titles[i]));
        }
    }

    /** Row in the synthetic layout: item, account, description, previous, final, base, notes. */
    private static void row(List<Cell> cells, int row, String item, String account, String description,
            String previous, String fin, String base, String notes) {
        cells.add(text(row, 1, item));
        cells.add(text(row, 2, account));
        cells.add(text(row, 3, description));
        cells.add(number(row, 4, previous));
        cells.add(number(row, 5, fin));
        if (base != null) {
            cells.add(number(row, 6, base));
        }
        if (notes != null) {
            cells.add(text(row, 7, notes));
        }
    }

    private static void account(List<Cell> cells, int row, String account, String plain, String q, String r) {
        cells.add(text(row, 1, account));
        cells.add(number(row, 2, plain));
        cells.add(number(row, 3, q));
        cells.add(number(row, 4, r));
    }

    private static Cell text(int row, int column, String value) {
        return new Cell(row, column, value, "text");
    }

    private static Cell number(int row, int column, String value) {
        return new Cell(row, column, value, "number");
    }
}
