package br.com.condominioauditoria.rag.indice;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.rag.model.document.ReadDocument;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Cell;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Page;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Paragraph;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Sheet;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Word;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Corte dos trechos pela localização (ADR 0003, Decisão 3): nunca atravessa página nem aba. */
public class CortadorTrechosTest {

    // ---------------------------------------------------------------- PDF

    @Test
    public void pdfUmTrechoPorPaginaComLinhasNaOrdemDaLeitura() {
        var p1 = pagina(1, List.of(
                new Word("mundo", 60, 90, 10.5, 20), // mesma linha de "Olá", um pouco mais baixa
                new Word("Olá", 10, 50, 10, 20),
                new Word("Segunda", 10, 60, 30, 40)));
        var p2 = pagina(2, List.of(new Word("Página", 10, 50, 10, 20), new Word("dois", 55, 80, 10, 20)));

        DocumentoCortado cortado = CortadorTrechos.cortar(pdf(p1, p2));

        assertThat(cortado.paginas()).isEqualTo(2);
        assertThat(cortado.trechos()).extracting(TrechoCortado::texto).containsExactly("Olá mundo\nSegunda",
                "Página dois");
        assertThat(cortado.trechos()).extracting(TrechoCortado::localizacao)
                .containsExactly(new Localizacao.Pagina(1), new Localizacao.Pagina(2));
        assertThat(cortado.trechos()).extracting(TrechoCortado::ordem).containsExactly(1, 2);
    }

    @Test
    public void pdfPaginaLongaViraVariosTrechosTodosDaMesmaPaginaComSobreposicao() {
        var palavras = new ArrayList<Word>();
        for (int linha = 0; linha < 200; linha++) { // 200 linhas de ~50 caracteres = ~10 mil caracteres
            palavras.add(new Word("linha%03d".formatted(linha), 10, 60, linha * 12.0, linha * 12.0 + 10));
            palavras.add(new Word("texto de exemplo para a página longa do PDF", 70, 300, linha * 12.0,
                    linha * 12.0 + 10));
        }
        var curta = pagina(4, List.of(new Word("fim", 10, 30, 10, 20)));

        DocumentoCortado cortado = CortadorTrechos.cortar(pdf(pagina(3, palavras), curta));

        List<TrechoCortado> daPagina3 = cortado.trechos().stream()
                .filter(t -> t.localizacao().equals(new Localizacao.Pagina(3))).toList();
        assertThat(daPagina3).hasSizeGreaterThanOrEqualTo(3);
        assertThat(daPagina3).allSatisfy(t -> assertThat(t.texto().length())
                .isLessThanOrEqualTo(CortadorTrechos.MAX_CARACTERES));
        // Todas as linhas da página aparecem em algum trecho
        String tudo = String.join("\n", daPagina3.stream().map(TrechoCortado::texto).toList());
        for (int linha = 0; linha < 200; linha++) {
            assertThat(tudo).contains("linha%03d ".formatted(linha));
        }
        // O trecho seguinte começa repetindo as últimas linhas do anterior (até 200 caracteres)
        List<String> linhasDoPrimeiro = List.of(daPagina3.get(0).texto().split("\n"));
        List<String> linhasDoSegundo = List.of(daPagina3.get(1).texto().split("\n"));
        int fimDaRepeticao = linhasDoSegundo.indexOf(linhasDoPrimeiro.getLast());
        assertThat(fimDaRepeticao).isBetween(0, 4);
        assertThat(linhasDoSegundo.subList(0, fimDaRepeticao + 1)).isEqualTo(
                linhasDoPrimeiro.subList(linhasDoPrimeiro.size() - fimDaRepeticao - 1, linhasDoPrimeiro.size()));
        assertThat(String.join("\n", linhasDoSegundo.subList(0, fimDaRepeticao + 1)).length())
                .isLessThanOrEqualTo(CortadorTrechos.SOBREPOSICAO);
        // A página 4 não se mistura com a 3
        assertThat(cortado.trechos().getLast().localizacao()).isEqualTo(new Localizacao.Pagina(4));
        assertThat(cortado.trechos().getLast().texto()).isEqualTo("fim");
    }

    @Test
    public void pdfPulaPaginaSemTextoENumeraNaOrdem() {
        var escaneada = new Page(1, 595, 842, "sem_texto", List.of());
        var comTexto = pagina(2, List.of(new Word("Ata", 10, 30, 10, 20)));

        DocumentoCortado cortado = CortadorTrechos.cortar(pdf(escaneada, comTexto));

        assertThat(cortado.paginas()).isEqualTo(2);
        assertThat(cortado.trechos()).singleElement().satisfies(t -> {
            assertThat(t.localizacao()).isEqualTo(new Localizacao.Pagina(2));
            assertThat(t.ordem()).isEqualTo(1);
        });
    }

    @Test
    public void pdfSemTextoNenhumTemMotivo() {
        var p1 = new Page(1, 595, 842, "sem_texto", List.of());
        var p2 = new Page(2, 595, 842, "sem_texto", List.of());

        DocumentoCortado cortado = CortadorTrechos.cortar(pdf(p1, p2));

        assertThat(cortado.semTexto()).isTrue();
        assertThat(cortado.paginas()).isEqualTo(2);
        assertThat(cortado.motivoSemTexto()).contains("sem texto extraível").contains("2 páginas");
    }

    @Test
    public void linhaGiganteSemEspacoEhCortadaSemPerderTexto() {
        String gigante = "x".repeat(7000);
        List<String> partes = CortadorTrechos.quebrarLinhaLonga(gigante, 3000);
        assertThat(partes).hasSize(3);
        assertThat(String.join("", partes)).isEqualTo(gigante);
    }

    // ---------------------------------------------------------------- Excel

    @Test
    public void excelBlocosDeTrintaLinhasRepetindoOCabecalho() {
        var celulas = new ArrayList<Cell>();
        celulas.add(new Cell(1, 1, "Data", "texto"));
        celulas.add(new Cell(1, 2, "Valor", "texto"));
        for (int linha = 2; linha <= 71; linha++) { // 70 linhas de dados
            celulas.add(new Cell(linha, 2, "%d.00".formatted(linha), "numero")); // fora de ordem de coluna
            celulas.add(new Cell(linha, 1, "2026-09-%02d".formatted(linha % 28 + 1), "data"));
        }

        DocumentoCortado cortado = CortadorTrechos.cortar(xlsx(new Sheet("Despesas", celulas)));

        assertThat(cortado.paginas()).isEqualTo(1);
        assertThat(cortado.trechos()).extracting(TrechoCortado::localizacao).containsExactly(
                new Localizacao.Planilha("Despesas", 2, 31),
                new Localizacao.Planilha("Despesas", 32, 61),
                new Localizacao.Planilha("Despesas", 62, 71));
        assertThat(cortado.trechos()).allSatisfy(t -> assertThat(t.texto()).startsWith("Data | Valor\n"));
        assertThat(cortado.trechos().getFirst().texto().split("\n")).hasSize(31);
        assertThat(cortado.trechos().getFirst().texto()).contains("2026-09-03 | 2.00");
    }

    @Test
    public void excelNuncaJuntaAbasEPulaAbaVazia() {
        var a = new Sheet("Janeiro", List.of(new Cell(1, 1, "Item", "texto"), new Cell(2, 1, "Água", "texto")));
        var vazia = new Sheet("Vazia", List.of(new Cell(1, 1, "  ", "texto")));
        var b = new Sheet("Fevereiro", List.of(new Cell(3, 1, "Só cabeçalho", "texto")));

        DocumentoCortado cortado = CortadorTrechos.cortar(xlsx(a, vazia, b));

        assertThat(cortado.paginas()).isEqualTo(3);
        assertThat(cortado.trechos()).extracting(TrechoCortado::localizacao).containsExactly(
                new Localizacao.Planilha("Janeiro", 2, 2),
                new Localizacao.Planilha("Fevereiro", 3, 3));
        assertThat(cortado.trechos()).extracting(TrechoCortado::texto).containsExactly("Item\nÁgua", "Só cabeçalho");
    }

    @Test
    public void excelSemCelulasEhSemTexto() {
        DocumentoCortado cortado = CortadorTrechos.cortar(xlsx(new Sheet("A", List.of())));
        assertThat(cortado.semTexto()).isTrue();
        assertThat(cortado.motivoSemTexto()).isNotBlank();
    }

    // ---------------------------------------------------------------- Word

    @Test
    public void wordAgrupaParagrafosAteOLimiteEPulaVazios() {
        var paragrafos = new ArrayList<Paragraph>();
        paragrafos.add(new Paragraph(1, "CONTRATO DE PRESTAÇÃO DE SERVIÇOS", null));
        paragrafos.add(new Paragraph(2, "   ", null));
        for (int i = 3; i <= 12; i++) { // 10 parágrafos de 1000 caracteres
            paragrafos.add(new Paragraph(i, "p%02d ".formatted(i) + "a".repeat(996), null));
        }

        DocumentoCortado cortado = CortadorTrechos.cortar(docx(paragrafos));

        assertThat(cortado.paginas()).isEqualTo(1);
        assertThat(cortado.trechos()).extracting(TrechoCortado::localizacao).containsExactly(
                new Localizacao.Paragrafos(1, 5, ""),
                new Localizacao.Paragrafos(6, 8, ""),
                new Localizacao.Paragrafos(9, 11, ""),
                new Localizacao.Paragrafos(12, 12, ""));
        assertThat(cortado.trechos()).allSatisfy(t -> assertThat(t.texto().length())
                .isLessThanOrEqualTo(CortadorTrechos.MAX_CARACTERES));
        assertThat(cortado.trechos().getFirst().texto()).startsWith("CONTRATO DE PRESTAÇÃO DE SERVIÇOS\np03 ");
    }

    @Test
    public void wordParagrafoLongoViraVariosTrechosDoMesmoParagrafo() {
        String longo = ("cláusula " + "b".repeat(40) + " ").repeat(200); // ~10 mil caracteres
        var paragrafos = List.of(new Paragraph(1, "Antes", null), new Paragraph(2, longo, null),
                new Paragraph(3, "Depois", null));

        DocumentoCortado cortado = CortadorTrechos.cortar(docx(paragrafos));

        assertThat(cortado.trechos().getFirst().localizacao()).isEqualTo(new Localizacao.Paragrafos(1, 1, ""));
        assertThat(cortado.trechos().getLast().localizacao()).isEqualTo(new Localizacao.Paragrafos(3, 3, ""));
        List<TrechoCortado> doLongo = cortado.trechos().subList(1, cortado.trechos().size() - 1);
        assertThat(doLongo).hasSizeGreaterThanOrEqualTo(3)
                .allSatisfy(t -> assertThat(t.localizacao()).isEqualTo(new Localizacao.Paragrafos(2, 2, "")));
    }

    @Test
    public void mesmoDocumentoGeraSempreOsMesmosTrechos() {
        var doc = docx(List.of(new Paragraph(1, "Um", null), new Paragraph(2, "Dois", null)));
        assertThat(CortadorTrechos.cortar(doc)).isEqualTo(CortadorTrechos.cortar(doc));
    }

    // ---------------------------------------------------------------- apoio

    private static Page pagina(int numero, List<Word> palavras) {
        return new Page(numero, 595, 842, "texto", palavras);
    }

    private static ReadDocument pdf(Page... paginas) {
        return new ReadDocument("1", "teste", arquivo("a.pdf"), "pdf", List.of(paginas), List.of(), List.of());
    }

    private static ReadDocument xlsx(Sheet... planilhas) {
        return new ReadDocument("1", "teste", arquivo("a.xlsx"), "xlsx", List.of(), List.of(planilhas), List.of());
    }

    private static ReadDocument docx(List<Paragraph> paragrafos) {
        return new ReadDocument("1", "teste", arquivo("a.docx"), "docx", List.of(), List.of(), paragrafos);
    }

    private static ReadDocument.FileInfo arquivo(String nome) {
        return new ReadDocument.FileInfo(nome, "a".repeat(64), 100);
    }
}
