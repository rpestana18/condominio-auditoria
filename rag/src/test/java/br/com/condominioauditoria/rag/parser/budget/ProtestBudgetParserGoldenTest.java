package br.com.condominioauditoria.rag.parser.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.rag.messaging.FileReceivedMessage;
import br.com.condominioauditoria.rag.messaging.MessageContract;
import br.com.condominioauditoria.rag.messaging.ProcessingResultMessage;
import br.com.condominioauditoria.rag.model.TotalsCheck;
import br.com.condominioauditoria.rag.model.budget.Budget;
import br.com.condominioauditoria.rag.model.budget.BudgetLine;
import br.com.condominioauditoria.rag.model.document.ReadDocument;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Page;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Word;
import br.com.condominioauditoria.rag.model.enums.BudgetLineMark;
import br.com.condominioauditoria.rag.model.enums.BudgetLineType;
import br.com.condominioauditoria.rag.parser.ReaderContract;
import br.com.condominioauditoria.rag.parser.cashflow.CashFlowParser;
import br.com.condominioauditoria.rag.service.calculator.BudgetCheck;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real 2026/2027 budget of the pilot (RF-03.1.1, RF-03.1.2 and RF-03.1.15). The reader's output for the PDF is real
 * data and stays out of git (data/golden/privado/po-2026-2027.documento-lido.json); without it, the test is skipped.
 */
public class ProtestBudgetParserGoldenTest {

    private static ReadDocument document;
    private static Budget budget;
    private static List<TotalsCheck> checks;

    @BeforeAll
    public static void read() throws Exception {
        Path json = Path.of(System.getProperty("golden.dir"), "privado/po-2026-2027.documento-lido.json");
        assumeTrue(Files.exists(json), "golden privado ausente");
        document = new ReaderContract().convert(Files.readString(json));
        var parser = new ProtestBudgetParser();
        assertThat(parser.recognizes(document)).isTrue();
        budget = parser.parse(document);
        checks = BudgetCheck.check(budget);
    }

    @Test
    public void recognitionByContent() throws Exception {
        assertThat(new CashFlowParser().recognizes(document)).isFalse();
        Path cashFlow = Path.of(System.getProperty("golden.dir"), "privado/fluxo-caixa-2026-09.documento-lido.json");
        if (Files.exists(cashFlow)) {
            assertThat(new ProtestBudgetParser().recognizes(new ReaderContract().convert(Files.readString(cashFlow))))
                    .isFalse();
        }
    }

    @Test
    public void header() {
        assertThat(budget.title()).isEqualTo("PROPOSTA ORÇAMENTÁRIA 2026 / 2027");
        assertThat(budget.printedFiscalYear()).isEqualTo("2026 / 2027");
        assertThat(budget.budgetColumns()).containsExactly("2025/2026", "2026/2027");
    }

    @Test
    public void allLines() {
        assertThat(budget.lines()).hasSize(98);
        assertThat(budget.lines()).allSatisfy(l -> assertThat(l.page()).isEqualTo(1));
        assertThat(budget.lines()).extracting(BudgetLine::sequence)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 98).boxed().toList());
        Map<BudgetLineType, Long> byType = budget.lines().stream()
                .collect(Collectors.groupingBy(BudgetLine::type, LinkedHashMap::new, Collectors.counting()));
        assertThat(byType).containsEntry(BudgetLineType.TOTAL, 1L).containsEntry(BudgetLineType.GRUPO, 9L)
                .containsEntry(BudgetLineType.LINHA, 88L);
        assertThat(budget.lines()).allSatisfy(l -> {
            assertThat(l.budgeted().scale()).isEqualTo(2);
            assertThat(l.previousBudgeted().scale()).isEqualTo(2);
            assertThat(l.description()).isNotBlank();
        });
    }

    /** RF-03.1.1, first criterion. */
    @Test
    public void line1320ProfessionalManagement() {
        BudgetLine l = line("1.3.20");
        assertThat(l.type()).isEqualTo(BudgetLineType.LINHA);
        assertThat(l.account()).isEqualTo("1682 - Sindicatura Profissional");
        assertThat(l.accountText()).isNull();
        assertThat(l.mark()).isNull();
        assertThat(l.description()).isEqualTo("Obm - Sergio Diniz");
        assertThat(l.previousBudgeted()).isEqualByComparingTo("17195.00");
        assertThat(l.budgeted()).isEqualByComparingTo("8000.00");
        assertThat(l.percentageText()).isEqualTo("-53,47%");
        assertThat(l.notes()).isEqualTo("Pro-labore Síndico");
        assertThat(l.page()).isEqualTo(1);
    }

    /** RF-03.1.1, second criterion. */
    @Test
    public void line1323Intercoms() {
        BudgetLine l = line("1.3.23");
        assertThat(l.account()).isEqualTo("1621 - Interfones");
        assertThat(l.description()).isEqualTo("Manutenção Preventiva De Interfones/Cftv");
        assertThat(l.previousBudgeted()).isEqualByComparingTo("0.00");
        assertThat(l.budgeted()).isEqualByComparingTo("0.00");
        assertThat(l.percentageText()).isNull();
        assertThat(l.notes()).isEqualTo("Manutenção R$4.100,00 out/25");
    }

    /** RF-03.1.1, third criterion, and the other marks of the account column. */
    @Test
    public void marks() {
        for (String code : List.of("1.4.1", "1.4.2", "1.4.3", "1.6.15")) {
            BudgetLine l = line(code);
            assertThat(l.mark()).as(code).isEqualTo(BudgetLineMark.RATEIO_A_PARTE);
            assertThat(l.account()).as(code).isNull();
            assertThat(l.budgeted()).as(code).isEqualByComparingTo("0.00");
        }
        assertThat(line("1.4.1").description()).isEqualTo("Força e Luz");
        assertThat(line("1.4.2").description()).isEqualTo("Água e Esgoto");
        assertThat(line("1.4.3").description()).isEqualTo("Gás");
        assertThat(line("1.6.15").description()).isEqualTo("Seguro predial");
        assertThat(line("1.6.11").mark()).isEqualTo(BudgetLineMark.SEM_VALOR);
        assertThat(line("1.6.12").mark()).isEqualTo(BudgetLineMark.NEGOCIADA_ISENCAO);
        assertThat(line("1.6.13").mark()).isEqualTo(BudgetLineMark.NEGOCIADA_ISENCAO);
        for (String code : List.of("1.5.3", "1.6.17", "1.7.6", "1.7.12")) {
            assertThat(line(code).mark()).as(code).isEqualTo(BudgetLineMark.VALOR_FIXO_SEM_REFERENCIA);
            assertThat(line(code).account()).as(code).isNull();
        }
        assertThat(line("1.6.17").budgeted()).isEqualByComparingTo("99.03");
        assertThat(budget.lines()).filteredOn(l -> l.mark() != null).hasSize(11);
    }

    /** Account column text that is neither a code nor a mark never becomes an account (ADR 0004, Decision 1). */
    @Test
    public void accountText() {
        BudgetLine gas = line("1.4.3");
        assertThat(gas.accountText()).isEqualTo("Débito em receitas eventuais");
        assertThat(gas.notes()).isEqualTo("Rateio à parte");
        assertThat(line("1").accountText()).isEqualTo("Soma das seções 1.1 a 1.9");
        assertThat(line("1.1").accountText()).isEqualTo("Subtotal (soma linhas 5 a 18)");
        assertThat(line("1.9").accountText()).isEqualTo("Fundos");
        assertThat(line("1.9.1").accountText()).isEqualTo("Fundo de Reserva");
        assertThat(line("1.9.2").accountText()).isEqualTo("Obras Reformas e Infraestrutura");
        assertThat(budget.lines()).filteredOn(l -> l.accountText() != null)
                .allSatisfy(l -> assertThat(l.account()).isNull());
        assertThat(budget.lines()).filteredOn(l -> l.type() == BudgetLineType.LINHA && l.mark() == null
                && !l.printedCode().startsWith("1.9.")).allSatisfy(l -> {
                    assertThat(l.account()).as(l.printedCode()).matches("^\\d{4} - .+");
                    assertThat(l.accountText()).as(l.printedCode()).isNull();
                });
    }

    /** Columns that touch the neighboring one. */
    @Test
    public void wideColumns() {
        assertThat(line("1.3.22").account()).isEqualTo("4066 - MONITORAMENTO REMOTO PORTARIA");
        assertThat(line("1.3.22").description()).isEqualTo("Câmeras Gabriel");
        assertThat(line("1.3.24").account()).isEqualTo("4069 - ASSESSORIA TECNICA ELEVADORES");
        assertThat(line("1.8.2").account()).isEqualTo("4071 - APOIO CENTRAL CORRESPONDENCIA");
        assertThat(line("1.8.2").description()).isEqualTo("Apoio central de correspondência - meses nov à jan");
        assertThat(line("1.2.1").account()).isEqualTo("1591 - Telefone Fixo + 1592 - Telefone");
        assertThat(line("1.3.5").description())
                .isEqualTo("Hilton Castro Magalhaes Locacao De Equipamentos Esportivos");
        assertThat(line("1.3.10").notes()).isEqualTo("Média jan 26/abr 26 + VIGILANTES RJ");
        assertThat(line("1.6.7").notes()).isEqualTo("-");
    }

    @Test
    public void amountsWithoutThousandsSeparator() {
        assertThat(line("1.1.5").budgeted()).isEqualByComparingTo("1585.14");
        assertThat(line("1.1.5").previousBudgeted()).isEqualByComparingTo("339.45");
        assertThat(line("1.1.9").budgeted()).isEqualByComparingTo("1189.16");
        assertThat(line("1.6.19").budgeted()).isEqualByComparingTo("1129.47");
        assertThat(line("1.8.6").budgeted()).isEqualByComparingTo("1000.00");
    }

    /** RF-03.1.2: 1.3.2 printed twice. Neither line is dropped or added to the other. */
    @Test
    public void code132RepeatedWithBothLines() {
        List<BudgetLine> repeatedLines = budget.lines().stream().filter(l -> l.printedCode().equals("1.3.2")).toList();
        assertThat(repeatedLines).extracting(BudgetLine::sequence).containsExactly(20, 43);
        assertThat(repeatedLines.get(0).account()).isEqualTo("1598 - Bombas");
        assertThat(repeatedLines.get(0).budgeted()).isEqualByComparingTo("3000.00");
        assertThat(repeatedLines.get(1).account()).isEqualTo("1624 - Caixa D'água");
        assertThat(repeatedLines.get(1).description()).isEqualTo("Caixa D'água");
        assertThat(repeatedLines.get(1).budgeted()).isEqualByComparingTo("1518.93");

        TotalsCheck repeated = check("CODIGO_REPETIDO");
        assertThat(repeated.ok()).isFalse();
        assertThat(repeated.detail()).isEqualTo("1.3.2 aparece 2 vezes (ordens 20 e 43)");
    }

    /**
     * RF-03.1.2, first criterion. The printed subtotals are those of the requirement, but in the PDF itself two sums do
     * not match to the cent (rounding in the source spreadsheet): the 1.3 lines sum to 336.274,18 (printed
     * 336.274,17) and the 1.9 lines sum to 22.581,00 (printed 22.581,01). The check is exact and points out both.
     */
    @Test
    public void subtotalsTotalAndMonthlyPlanned() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("1.1", "69193.86");
        expected.put("1.2", "694.05");
        expected.put("1.3", "336274.17");
        expected.put("1.4", "0.00");
        expected.put("1.5", "2850.00");
        expected.put("1.6", "17388.04");
        expected.put("1.7", "15200.00");
        expected.put("1.8", "10020.00");
        expected.put("1.9", "22581.01");
        assertThat(budget.lines()).filteredOn(l -> l.type() == BudgetLineType.GRUPO).extracting(BudgetLine::printedCode)
                .containsExactlyElementsOf(expected.keySet());
        expected.forEach((code, amount) -> assertThat(line(code).budgeted()).as(code).isEqualByComparingTo(amount));

        List<TotalsCheck> subtotals = checks.stream().filter(v -> v.code().equals("SUBTOTAL_GRUPO")).toList();
        assertThat(subtotals).extracting(TotalsCheck::detail).containsExactly(
                "1.1 PESSOAL: soma das linhas 69.193,86; impresso 69.193,86",
                "1.2 CONSUMO/UTILIDADES: soma das linhas 694,05; impresso 694,05",
                "1.3 SERVIÇOS - CONTRATOS EFETIVOS: soma das linhas 336.274,18; impresso 336.274,17; diferença -0,01",
                "1.4 TARIFAS PÚBLICAS: soma das linhas 0,00; impresso 0,00",
                "1.5 AQUISIÇÃO DE BENS: soma das linhas 2.850,00; impresso 2.850,00",
                "1.6 DESPESAS ADMINISTRATIVAS: soma das linhas 17.388,04; impresso 17.388,04",
                "1.7 MATERIAIS/SUPRIMENTOS: soma das linhas 15.200,00; impresso 15.200,00",
                "1.8 SERVIÇOS: soma das linhas 10.020,00; impresso 10.020,00",
                "1.9 Fundos do Condomínio: soma das linhas 22.581,00; impresso 22.581,01; diferença 0,01");
        assertThat(subtotals).extracting(TotalsCheck::ok)
                .containsExactly(true, true, false, true, true, true, true, true, false);

        assertThat(line("1").budgeted()).isEqualByComparingTo("474201.13");
        assertThat(check("TOTAL").ok()).isTrue();
        assertThat(check("TOTAL").detail()).isEqualTo("soma dos grupos 474.201,13; impresso 474.201,13");
        assertThat(check("PREVISTO_MES").ok()).isTrue();
        assertThat(check("PREVISTO_MES").detail())
                .isEqualTo("474.201,13 - 22.581,01 = 451.620,12; soma das linhas dos demais grupos 451.620,13");
        assertThat(BudgetCheck.monthlyPlanned(budget)).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("451620.12"));
        assertThat(checks).extracting(TotalsCheck::code).containsExactly("SUBTOTAL_GRUPO", "SUBTOTAL_GRUPO",
                "SUBTOTAL_GRUPO", "SUBTOTAL_GRUPO", "SUBTOTAL_GRUPO", "SUBTOTAL_GRUPO", "SUBTOTAL_GRUPO",
                "SUBTOTAL_GRUPO", "SUBTOTAL_GRUPO", "TOTAL", "PREVISTO_MES", "FUNDO_TAXA", "CODIGO_REPETIDO");
    }

    /** RF-03.1.3: reserve 3% and works 2% of 451.620,12. */
    @Test
    public void fundsByRate() {
        assertThat(line("1.9.1").budgeted()).isEqualByComparingTo("13548.60");
        assertThat(line("1.9.1").percentageText()).isEqualTo("3,00%");
        assertThat(line("1.9.2").budgeted()).isEqualByComparingTo("9032.40");
        assertThat(line("1.9.2").percentageText()).isEqualTo("2,00%");
        TotalsCheck rate = check("FUNDO_TAXA");
        assertThat(rate.ok()).isTrue();
        assertThat(rate.detail()).isEqualTo("1.9.1 Fundo de Reserva: 3,00% de 451.620,12 = 13.548,60; impresso 13.548,60; "
                + "1.9.2 Fundo de Obras: 2,00% de 451.620,12 = 9.032,40; impresso 9.032,40");
    }

    /** RF-03.1.2, second criterion: copy with the Personnel subtotal printed as 69.193,00. */
    @Test
    public void copyWithChangedSubtotalFailsCheck() {
        ReadDocument copy = replaceWord(document, "69.193,86", "69.193,00");
        Budget changed = new ProtestBudgetParser().parse(copy);
        assertThat(changed.lines().stream().filter(l -> l.printedCode().equals("1.1")).findFirst().orElseThrow()
                .budgeted()).isEqualByComparingTo("69193.00");

        List<TotalsCheck> result = BudgetCheck.check(changed);
        TotalsCheck personnel = result.getFirst();
        assertThat(personnel.code()).isEqualTo("SUBTOTAL_GRUPO");
        assertThat(personnel.ok()).isFalse();
        assertThat(personnel.detail()).isEqualTo("1.1 PESSOAL: soma das linhas 69.193,86; impresso 69.193,00; diferença -0,86");
        TotalsCheck total = result.stream().filter(v -> v.code().equals("TOTAL")).findFirst().orElseThrow();
        assertThat(total.ok()).isFalse();
        assertThat(total.detail()).isEqualTo("soma dos grupos 474.200,27; impresso 474.201,13; diferença 0,86");
        // In the original budget, Personnel and the total match: the failure comes only from the change
        assertThat(checks.getFirst().ok()).isTrue();
        assertThat(check("TOTAL").ok()).isTrue();
    }

    /** The reading fits the v2 contract and matches, field by field, the lines of the contract example. */
    @Test
    public void resultInV2ContractEqualsExample() throws Exception {
        var file = new FileReceivedMessage(1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PO",
                "po.pdf", "x/po.pdf", "a".repeat(64));
        byte[] json = new MessageContract().write(ProcessingResultMessage.completedBudget(file, "po-protest", 1, budget,
                checks));
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode produced = mapper.readTree(json).get("previsaoOrcamentaria");
        JsonNode example = mapper.readTree(Files.readString(Path.of(System.getProperty("contratos.dir"),
                "mensagens/v2/exemplos/resultado-concluido-po.json"))).get("previsaoOrcamentaria");

        assertThat(produced.get("titulo")).isEqualTo(example.get("titulo"));
        assertThat(produced.get("colunasOrcado")).isEqualTo(example.get("colunasOrcado"));
        for (JsonNode exampleLine : example.get("linhas")) {
            int sequence = exampleLine.get("ordem").asInt();
            assertThat(produced.get("linhas").get(sequence - 1)).as("ordem " + sequence).isEqualTo(exampleLine);
        }
    }

    private static BudgetLine line(String code) {
        return budget.lines().stream().filter(l -> l.printedCode().equals(code)).findFirst().orElseThrow();
    }

    private static TotalsCheck check(String code) {
        return checks.stream().filter(v -> v.code().equals(code)).findFirst().orElseThrow();
    }

    private static ReadDocument replaceWord(ReadDocument d, String from, String to) {
        List<Page> pages = d.pages().stream().map(p -> new Page(p.number(), p.width(), p.height(), p.method(),
                p.words().stream().map(w -> w.text().equals(from)
                        ? new Word(to, w.x0(), w.x1(), w.top(), w.bottom()) : w).toList())).toList();
        return new ReadDocument(d.contractVersion(), d.reader(), d.file(), d.type(), pages, d.sheets(),
                d.paragraphs());
    }
}
