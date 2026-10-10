package br.com.condominioauditoria.rag.parser.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.rag.model.budget.Budget;
import br.com.condominioauditoria.rag.model.budget.BudgetLine;
import br.com.condominioauditoria.rag.model.document.ReadDocument;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Page;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Word;
import br.com.condominioauditoria.rag.model.enums.BudgetLineMark;
import br.com.condominioauditoria.rag.model.enums.BudgetLineType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Budget parser with a hand-built page, with the positions copied from the pilot budget (runs without the real data).
 * The full golden test is ProtestBudgetParserGoldenTest.
 */
public class ProtestBudgetParserTest {

    private final ProtestBudgetParser parser = new ProtestBudgetParser();

    @Test
    public void recognizesAndReadsLinesWithAmountMarkAndJoinedWord() {
        ReadDocument document = document(new Page(1, 595.32, 841.92, "texto", budgetWords()));

        assertThat(parser.recognizes(document)).isTrue();
        Budget budget = parser.parse(document);

        assertThat(budget.title()).isEqualTo("PROPOSTA ORÇAMENTÁRIA 2026 / 2027");
        assertThat(budget.printedFiscalYear()).isEqualTo("2026 / 2027");
        assertThat(budget.budgetColumns()).containsExactly("2025/2026", "2026/2027");
        assertThat(budget.lines()).extracting(BudgetLine::printedCode).containsExactly("1", "1.1", "1.1.5", "1.4.3",
                "1.8.2");
        assertThat(budget.lines()).extracting(BudgetLine::type)
                .containsExactly(BudgetLineType.TOTAL, BudgetLineType.GROUP, BudgetLineType.LINE,
                        BudgetLineType.LINE, BudgetLineType.LINE);

        BudgetLine vacation = budget.lines().get(2);
        assertThat(vacation.account()).isEqualTo("1553 - Férias");
        assertThat(vacation.description()).isEqualTo("Provisão de Férias");
        assertThat(vacation.previousBudgeted()).isEqualByComparingTo("339.45");
        assertThat(vacation.budgeted()).isEqualByComparingTo("1585.14");
        assertThat(vacation.percentageText()).isEqualTo("366,97%");
        assertThat(vacation.notes()).isEqualTo("Média ago 25/abr 26");

        BudgetLine gas = budget.lines().get(3);
        assertThat(gas.account()).isNull();
        assertThat(gas.accountText()).isEqualTo("Débito em receitas eventuais");
        assertThat(gas.mark()).isEqualTo(BudgetLineMark.SEPARATE_APPORTIONMENT);
        assertThat(gas.percentageText()).isNull();
        assertThat(gas.notes()).isEqualTo("Rateio à parte");

        BudgetLine support = budget.lines().get(4);
        assertThat(support.account()).isEqualTo("4071 - APOIO CENTRAL CORRESPONDENCIA");
        assertThat(support.description()).isEqualTo("Apoio central de correspondência");

        assertThat(budget.lines().get(1).accountText()).isEqualTo("Subtotal (soma linhas 5 a 18)");
        assertThat(budget.lines().get(1).account()).isNull();
    }

    @Test
    public void marksFromAccountColumn() {
        List<Word> words = new ArrayList<>(header());
        words.addAll(List.of(p("1.6", 96, 103, 462.5), p("Subtotal", 115, 135, 462.5),
                p("DESPESAS", 200, 223, 462.5), p("0,00", 350, 358, 462.5), p("0,00", 388, 396, 462.5)));
        words.addAll(List.of(p("1.6.11", 96, 107, 536.3), p("Sem", 115, 123, 536.3), p("valor", 124, 134, 536.3),
                p("(R$", 135, 141, 536.3), p("0,00)", 142, 152, 536.3), p("Locação", 200, 216, 536.3),
                p("0,00", 350, 358, 536.3), p("0,00", 388, 396, 536.3)));
        words.addAll(List.of(p("1.6.12", 96, 107, 543.0), p("Negociada", 115, 135, 543.0),
                p("isenção", 136, 150, 543.0), p("Reemb.", 200, 215, 543.0), p("0,00", 350, 358, 543.0),
                p("0,00", 388, 396, 543.0)));
        words.addAll(List.of(p("1.6.17", 96, 107, 576.6), p("Valor", 115, 125, 576.6), p("fixo", 126, 133, 576.6),
                p("(sem", 134, 143, 576.6), p("referência)", 144, 165, 576.6), p("Multi", 200, 210, 576.6),
                p("99,03", 347, 358, 576.6), p("99,03", 385, 396, 576.6)));
        words.addAll(List.of(p("1.6.15", 96, 107, 563.2), p("Rateio", 115, 127, 563.2), p("à", 128, 131, 563.2),
                p("parte", 132, 142, 563.2), p("Seguro", 200, 213, 563.2), p("0,00", 350, 358, 563.2),
                p("0,00", 388, 396, 563.2)));

        Budget budget = parser.parse(document(new Page(1, 595, 842, "texto", words)));

        assertThat(budget.lines()).extracting(BudgetLine::mark).containsExactly(null, BudgetLineMark.NO_AMOUNT,
                BudgetLineMark.NEGOTIATED_EXEMPTION, BudgetLineMark.SEPARATE_APPORTIONMENT,
                        BudgetLineMark.FIXED_AMOUNT_NO_REFERENCE);
        assertThat(budget.lines().subList(1, 5)).allSatisfy(l -> {
            assertThat(l.account()).isNull();
            assertThat(l.accountText()).isNull();
        });
    }

    @Test
    public void lineWithAmountWithoutCodeIsRejected() {
        List<Word> words = new ArrayList<>(header());
        words.addAll(List.of(p("Outros", 115, 135, 130), p("12,00", 350, 358, 130), p("13,00", 388, 396, 130)));

        assertThatThrownBy(() -> parser.parse(document(new Page(1, 595, 842, "texto", words))))
                .hasMessageContaining("Linha com valor e sem código");
    }

    @Test
    public void scannedBudgetFailsWithReadableReason() {
        ReadDocument scanned = document(new Page(1, 595, 842, "sem_texto", List.of()));

        assertThat(parser.recognizes(scanned)).isFalse();
        assertThat(ProtestBudgetParser.noText(scanned)).isTrue();
        assertThatThrownBy(() -> parser.parse(scanned)).hasMessage("PO sem texto; OCR ainda não disponível");
    }

    @Test
    public void joinedWordWithoutCutPointStaysWhole() {
        Word p = p("ABCDEFGH", 180, 220, 10);
        assertThat(ProtestBudgetParser.splitWord(p, 200)).containsExactly(p);
        assertThat(ProtestBudgetParser.splitWord(p("CORRESPONDENCIAApoio", 160, 211, 10), 200))
                .extracting(Word::text).containsExactly("CORRESPONDENCIA", "Apoio");
    }

    private static List<Word> budgetWords() {
        List<Word> w = new ArrayList<>(header());
        double t = 109.8;
        w.addAll(List.of(p("1", 96, 99, t), p("Soma", 115, 131, t), p("das", 132, 141, t), p("seções", 143, 161, t),
                p("1.1", 163, 171, t), p("a", 173, 176, t), p("1.9", 177, 186, t), p("TOTAL", 201, 218, t),
                p("DAS", 220, 231, t), p("DESPESAS", 232, 260, t), p("441.304,38", 327, 358, t),
                p("474.201,13", 366, 396, t), p("7,45%", 402, 419, t)));
        t = 123.4;
        w.addAll(List.of(p("1.1", 96, 103, t), p("Subtotal", 115, 135, t), p("(soma", 136, 150, t),
                p("linhas", 151, 165, t), p("5", 166, 169, t), p("a", 170, 173, t), p("18)", 174, 181, t),
                p("PESSOAL", 200, 221, t), p("37.661,43", 336, 358, t), p("69.193,86", 374, 396, t),
                p("83,73%", 402, 419, t)));
        t = 157.5;
        w.addAll(List.of(p("1.1.5", 96, 105, t), p("1553", 115, 125, t), p("-", 126, 127, t), p("Férias", 128, 139, t),
                p("Provisão", 200, 217, t), p("de", 218, 223, t), p("Férias", 224, 235, t), p("339,45", 345, 358, t),
                p("1585,14", 381, 396, t), p("366,97%", 402, 419, t), p("Média", 432, 444, t), p("ago", 445, 452, t),
                p("25/abr", 453, 466, t), p("26", 467, 472, t)));
        t = 428.2;
        w.addAll(List.of(p("1.4.3", 96, 105, t), p("Débito", 115, 128, t), p("em", 129, 135, t),
                p("receitas", 136, 151, t), p("eventuais", 152, 170, t), p("Gás", 200, 207, t), p("0,00", 350, 358, t),
                p("0,00", 388, 396, t), p("Rateio", 450, 461, t), p("à", 462, 464, t), p("parte", 465, 474, t)));
        t = 711.9;
        w.addAll(List.of(p("1.8.2", 96, 105, t), p("4071", 115, 125, t), p("-", 126, 127, t), p("APOIO", 128, 141, t),
                p("CENTRAL", 142, 159, t), p("CORRESPONDENCIAApoio", 160, 211, t), p("central", 213, 226, t),
                p("de", 227, 232, t), p("correspondência", 233, 264, t), p("1.250,00", 341, 358, t),
                p("0,00", 388, 396, t), p("-100,00%", 402, 420, t)));
        return w;
    }

    private static List<Word> header() {
        return List.of(p("PROPOSTA", 202, 261, 65.1), p("ORÇAMENTÁRIA", 264, 353, 65.1), p("2026", 356, 383, 65.1),
                p("/", 386, 391, 65.1), p("2027", 394, 421, 65.1),
                p("ORÇADO", 329, 352, 88.7), p("ORÇADO", 367, 390, 88.7), p("%", 408, 413, 88.7),
                p("Item", 98, 110, 92.8), p("IDENTIFICAÇÃO", 124, 165, 92.8), p("DESPESA", 166, 190, 92.8),
                p("DESCRIÇÃO", 246, 276, 92.8), p("Observações", 445, 479, 92.8),
                p("2025/2026", 326, 355, 97.0), p("2026/2027", 364, 393, 97.0), p("Orçado", 401, 420, 97.0));
    }

    private static Word p(String text, double x0, double x1, double top) {
        return new Word(text, x0, x1, top, top + 5);
    }

    private static ReadDocument document(Page page) {
        return new ReadDocument("1", "leitor-py", new ReadDocument.FileInfo("po.pdf", "a".repeat(64), 1), "pdf",
                List.of(page), List.of(), List.of());
    }
}
