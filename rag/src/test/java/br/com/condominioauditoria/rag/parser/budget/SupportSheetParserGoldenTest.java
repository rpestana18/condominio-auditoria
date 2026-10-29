package br.com.condominioauditoria.rag.parser.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.rag.model.budget.BudgetLineOrigin;
import br.com.condominioauditoria.rag.model.budget.BudgetLineOrigins;
import br.com.condominioauditoria.rag.model.document.ReadDocument;
import br.com.condominioauditoria.rag.parser.ReaderContract;
import br.com.condominioauditoria.rag.parser.cashflow.CashFlowParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Golden of the pilot's support sheet (data/golden/privado/po-2026-2027-planilha-apoio, outside git; RF-13.13, ADR
 * 0009 Decision 3 B). The analysis of the {@code negocio} agent (po-2026-2027-origem-das-linhas.md, read from the
 * formulas) counts 19 by index, 7 equal to the average, 46 typed and 14 without an account average. The deduction by
 * the numbers gives 47 typed and 13 without an average: the base of 1.3.23 Interfones (455,56) was typed in the sheet
 * but equals the August–April average of account 1621 (4.100,00 ÷ 9), so it is taken as that average. Known limit of
 * option B; the Admin corrects the origin of that line.
 */
public class SupportSheetParserGoldenTest {

    private BudgetLineOrigins origins;
    private Map<Integer, BudgetLineOrigin> byRow;

    @BeforeEach
    public void read() throws Exception {
        Path file = Path.of(System.getProperty("golden.dir"), "privado",
                "po-2026-2027-planilha-apoio.read-document.json");
        assumeTrue(Files.exists(file), "golden privado ausente");
        ReadDocument document = new ReaderContract().convert(Files.readString(file));
        var parser = new SupportSheetParser();
        assertThat(parser.recognizes(document)).isTrue();
        assertThat(new CashFlowParser().recognizes(document)).isFalse();
        assertThat(new ProtestBudgetParser().recognizes(document)).isFalse();
        origins = parser.parse(document);
        byRow = origins.lines().stream().collect(Collectors.toMap(BudgetLineOrigin::row, Function.identity()));
    }

    @Test
    public void the86ExpenseLinesWithTheMethodDeducedByTheNumbers() {
        assertThat(origins.sheet()).isEqualTo("PO-26-27");
        assertThat(origins.expensesSheet()).isEqualTo("despesas-25-26");
        assertThat(origins.indexSheet()).isEqualTo("dissidio-impostos");
        assertThat(origins.lines()).hasSize(86);
        Map<String, Long> methods = origins.lines().stream()
                .collect(Collectors.groupingBy(BudgetLineOrigin::method, Collectors.counting()));
        assertThat(methods).containsEntry("AVERAGE_TIMES_INDEX", 19L).containsEntry("EQUAL_TO_AVERAGE", 7L)
                .containsEntry("TYPED", 47L).containsEntry("NO_AVERAGE", 13L);
    }

    @Test
    public void vigiaEPortaria1310() {
        BudgetLineOrigin l = byRow.get(30);
        assertThat(l.account()).isEqualTo("1692 - Vigia e Portaria");
        assertThat(l.sourceAccounts()).containsExactly("1692");
        assertThat(l.averageFrom()).isEqualTo("2026-01");
        assertThat(l.averageTo()).isEqualTo("2026-04");
        assertThat(l.averageAmount()).isEqualByComparingTo("74627.40");
        assertThat(l.method()).isEqualTo("AVERAGE_TIMES_INDEX");
        assertThat(l.indexName()).isEqualTo("VIGILANTES RJ");
        assertThat(l.indexRate()).isEqualByComparingTo("0.0723");
        assertThat(l.indexForecast()).isFalse();
        assertThat(l.finalAmount()).isEqualByComparingTo("80022.96");
    }

    @Test
    public void jardinagem138IsTypedWithTheReason() {
        BudgetLineOrigin l = byRow.get(28);
        assertThat(l.printedCode()).isEqualTo("1.3.8");
        assertThat(l.averageAmount()).isEqualByComparingTo("13480.00");
        assertThat(l.method()).isEqualTo("TYPED");
        assertThat(l.finalAmount()).isEqualByComparingTo("18000.00");
        assertThat(l.reason()).isEqualTo("Negociação contratual");
    }

    @Test
    public void staffWithTheForecastDissidio() {
        BudgetLineOrigin l = byRow.get(5);
        assertThat(l.printedCode()).isEqualTo("1.1.1");
        assertThat(l.averageFrom()).isEqualTo("2025-08");
        assertThat(l.averageAmount()).isEqualByComparingTo("33740.99");
        assertThat(l.indexName()).isEqualTo("DISSÍDIO SEEMRJ");
        assertThat(l.indexRate()).isEqualByComparingTo("0.065");
        assertThat(l.indexForecast()).isTrue();
        assertThat(l.finalAmount()).isEqualByComparingTo("35934.15");
    }

    @Test
    public void igpmLabelWithTheValueOfIpcaIsNoted() {
        BudgetLineOrigin radios = origins.lines().stream().filter(l -> "1.3.9".equals(l.printedCode())).findFirst()
                .orElseThrow();
        assertThat(radios.indexName()).isEqualTo("IGP-M");
        assertThat(radios.indexNote()).isEqualTo("rótulo IGP-M, valor aplicado 4,44% (igual ao IPCA ACUMULADO)");
    }

    /** v5 (ADR 0009, Decision 8): the accounts of the expenses sheet with the group deduced from the sums. */
    @Test
    public void expenseAccountsWithTheGroupDeducedFromTheSums() {
        var byCode = new java.util.HashMap<String, br.com.condominioauditoria.rag.model.budget.ExpenseAccount>();
        origins.accounts().forEach(a -> byCode.put(a.code(), a));
        assertThat(origins.accounts()).hasSize(133);
        assertThat(byCode.get("1547").name()).isEqualTo("Adiantamento Quinzenal");
        assertThat(byCode.get("1547").parentCode()).isEqualTo("1545");
        assertThat(byCode.get("1546").parentCode()).isEqualTo("1545");
        assertThat(byCode.get("1545").parentCode()).isNull();
        assertThat(byCode.get("1557").parentCode()).isEqualTo("1556");
        assertThat(byCode.get("1570").parentCode()).isEqualTo("1569");
        assertThat(byCode.get("1636").parentCode()).isEqualTo("1635");
        assertThat(byCode.get("1703").parentCode()).isEqualTo("1702");
        assertThat(byCode.get("1765").parentCode()).isEqualTo("1753");
        assertThat(byCode.get("1738").parentCode()).isEqualTo("1736");
        assertThat(byCode.get("1692").parentCode()).isNull();
    }
}
