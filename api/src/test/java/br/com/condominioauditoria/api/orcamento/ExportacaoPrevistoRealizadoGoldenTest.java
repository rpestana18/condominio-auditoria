package br.com.condominioauditoria.api.orcamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.api.service.audit.ConductTerms;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * RF-03.1.14 com setembro/2026 do piloto (golden privado): o mesmo resultado vira JSON (o que a API devolve), PDF e
 * Excel, e os números do PDF e do Excel são iguais aos do JSON, centavo a centavo; "PROVISÓRIO" com a lista das
 * compras a realocar; nenhum termo de conduta (RF-04.15); sem gráfico. Pulado sem data/golden/privado.
 */
class ExportacaoPrevistoRealizadoGoldenTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Instant GERADO_EM = Instant.parse("2026-10-04T15:30:00Z");

    @Test
    void numerosDoPdfIguaisAosDoJson() throws IOException {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        var calculo = g.cenario.previstoRealizado.calcular(g.cenario.condominioId, "2026-09", null);
        JsonNode json = JSON.readTree(JSON.writeValueAsString(calculo.resultado()));
        RelatorioPrevistoRealizado rel = relatorio(calculo);

        String texto = textoDoPdf(new RelatorioPdf().gerar(rel));

        // Critério do RF-03.1.14
        assertThat(texto).contains("446.176,89", "451.620,13", "98,8%", "38.880,19", "8,6%");
        JsonNode totais = json.get("totais");
        assertThat(texto).contains(seq(totais, "previsto", "despesaRealizada", "emLinhas", "diferenca")
                + " " + pct(totais.get("execucao")));
        JsonNode regra = json.get("regra20");
        assertThat(texto).contains(din(regra.get("excesso")) + " " + pct(regra.get("percentual")) + " "
                + din(regra.get("limite")));
        int linhas = 0;
        for (JsonNode grupo : json.get("grupos")) {
            assertThat(texto).as("grupo %s", grupo.get("codigo").asString())
                    .contains(seq(grupo, "previsto", "realizado", "diferenca") + " " + pct(grupo.get("execucao")));
            for (JsonNode l : grupo.get("linhas")) {
                assertThat(texto).as("linha %s", l.get("codigo").asString())
                        .contains(seq(l, "previsto", "realizado", "diferenca") + " " + pct(l.get("execucao")));
                linhas++;
            }
        }
        assertThat(linhas).isGreaterThanOrEqualTo(70);
        for (JsonNode f : json.get("fundos")) {
            if ("COMPARADO".equals(f.get("situacao").asString())) {
                assertThat(texto).contains(seq(f, "previsto", "arrecadado", "diferenca") + " " + pct(f.get("execucao")));
            }
        }
        // Cabeçalho
        assertThat(texto).contains(g.cenario.arquivos.get(g.po.getFileId()).getOriginalName(), "versão 1", "exercício 05/2026 a 04/2027",
                g.po.getSha256(), "Período 09/2026", "04/10/2026 12:30", "Admin Teste (admin)",
                "73 de 73 contas confirmadas");
    }

    @Test
    void numerosDoExcelIguaisAosDoJsonComEvidencia() throws IOException {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        var calculo = g.cenario.previstoRealizado.calcular(g.cenario.condominioId, "2026-09", null);
        JsonNode json = JSON.readTree(JSON.writeValueAsString(calculo.resultado()));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(new RelatorioExcel().gerar(relatorio(calculo))))) {
            Sheet resumo = wb.getSheet(RelatorioExcel.ABA_RESUMO);
            Map<String, Row> linhas = new HashMap<>();
            Map<String, Row> grupos = new HashMap<>();
            for (Row r : resumo) {
                String tipo = texto(r.getCell(0));
                if ("Linha".equals(tipo)) {
                    linhas.put(texto(r.getCell(1)), r);
                } else if ("Grupo".equals(tipo)) {
                    grupos.put(texto(r.getCell(1)), r);
                }
            }
            int conferidas = 0;
            for (JsonNode grupo : json.get("grupos")) {
                Row gr = grupos.get(grupo.get("codigo").asString());
                assertThat(gr).isNotNull();
                assertIgual(gr, 4, grupo.get("previsto"));
                assertIgual(gr, 5, grupo.get("realizado"));
                assertIgual(gr, 6, grupo.get("diferenca"));
                for (JsonNode l : grupo.get("linhas")) {
                    Row lr = linhas.get(l.get("codigo").asString());
                    assertThat(lr).as("linha %s no Excel", l.get("codigo").asString()).isNotNull();
                    assertIgual(lr, 4, l.get("previsto"));
                    assertIgual(lr, 5, l.get("realizado"));
                    assertIgual(lr, 6, l.get("diferenca"));
                    assertIgual(lr, 7, l.get("execucao"));
                    // Dinheiro como número com máscara, nunca fórmula
                    assertThat(lr.getCell(5).getCellType()).isEqualTo(CellType.NUMERIC);
                    assertThat(lr.getCell(5).getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
                    conferidas++;
                }
            }
            assertThat(conferidas).isEqualTo(linhas.size());
            Row totais = linhaAbaixo(resumo, "Totais do fundo Condomínio", 2);
            assertIgual(totais, 0, json.get("totais").get("previsto"));
            assertIgual(totais, 1, json.get("totais").get("despesaRealizada"));
            assertIgual(totais, 4, json.get("totais").get("execucao"));
            assertThat(totais.getCell(1).getNumericCellValue()).isEqualTo(446176.89);
            Row regra = linhaAbaixo(resumo, "Regra dos 20% (Conv. 16.2)", 2);
            assertIgual(regra, 0, json.get("regra20").get("excesso"));
            assertIgual(regra, 1, json.get("regra20").get("percentual"));
            assertThat(regra.getCell(0).getNumericCellValue()).isEqualTo(38880.19);
            assertThat(regra.getCell(1).getNumericCellValue()).isEqualTo(8.6);

            // Aba de evidência: um lançamento por linha, com arquivo, página e hash; soma igual ao realizado
            Sheet evidencia = wb.getSheet(RelatorioExcel.ABA_EVIDENCIA);
            // Todos os números comparados: linhas, blocos à parte e arrecadação dos fundos ligados às linhas 1.9
            // (os créditos de fundos sem previsto na PO não compõem número nenhum e ficam fora)
            java.util.Set<String> ligados = new java.util.HashSet<>();
            calculo.resultado().fundos().stream().filter(f -> f.linhaCodigo() != null && f.fundoId() != null)
                    .forEach(f -> ligados.add(CalculoPrevistoRealizado.alvoFundo(f.fundoId())));
            int total = calculo.evidencias().entrySet().stream()
                    .filter(x -> !x.getKey().startsWith("fundo:") || ligados.contains(x.getKey()))
                    .mapToInt(x -> x.getValue().size()).sum();
            assertThat(evidencia.getLastRowNum()).isEqualTo(total);
            BigDecimal portaria = BigDecimal.ZERO;
            for (Row r : evidencia) {
                if (r.getRowNum() == 0) {
                    continue;
                }
                assertThat(texto(r.getCell(9))).isEqualTo(g.arquivo.getOriginalName());
                assertThat(r.getCell(10).getNumericCellValue()).isPositive();
                assertThat(texto(r.getCell(12))).hasSize(64);
                if (texto(r.getCell(0)).startsWith("1.3.10 ")) {
                    portaria = portaria.add(BigDecimal.valueOf(r.getCell(7).getNumericCellValue()));
                }
            }
            assertThat(portaria).isEqualByComparingTo("86816.34");
            // Sem gráfico
            assertThat(((XSSFSheet) resumo).getDrawingPatriarch()).isNull();
            assertThat(((XSSFSheet) evidencia).getDrawingPatriarch()).isNull();
        }
    }

    @Test
    void provisorioComAListaDasComprasARealocar() throws IOException {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        var calculo = g.cenario.previstoRealizado.calcular(g.cenario.condominioId, "2026-09", null);
        RelatorioPrevistoRealizado rel = relatorio(calculo);
        var compras = calculo.evidencias().get(CalculoPrevistoRealizado.ALVO_A_REALOCAR);
        assertThat(compras).isNotEmpty();

        String pdf = textoDoPdf(new RelatorioPdf().gerar(rel));
        List<String> excel = textosDoExcel(new RelatorioExcel().gerar(rel));

        assertThat(rel.provisorio()).isTrue();
        assertThat(pdf).contains("PROVISÓRIO", "Há valores fora das linhas da PO");
        assertThat(excel).contains("PROVISÓRIO");
        assertThat(rel.pendencias()).hasSize(compras.size()).allMatch(p -> p.bloco().equals("A realocar"));
        BigDecimal soma = BigDecimal.ZERO;
        for (var c : compras) {
            assertThat(pdf).contains("A realocar " + RelatorioPrevistoRealizado.data(c.data()) + " " + c.conta() + " ");
            assertThat(pdf).contains(DinheiroBr.formatar(c.valor()));
            soma = soma.add(c.valor());
        }
        assertThat(soma).isEqualByComparingTo("1050.93");
        assertThat(excel.stream().filter(t -> t.equals("A realocar")).count()).isGreaterThanOrEqualTo(compras.size());
    }

    @Test
    void nenhumTermoDeCondutaESemGrafico() throws IOException {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        for (String periodo : List.of("2026-09", "acumulado")) {
            var calculo = g.cenario.previstoRealizado.calcular(g.cenario.condominioId, periodo, null);
            RelatorioPrevistoRealizado rel = relatorio(calculo);
            byte[] pdf = new RelatorioPdf().gerar(rel);

            assertThat(ConductTerms.find(new RelatorioPdf().html(rel))).as("HTML %s", periodo).isEmpty();
            assertThat(ConductTerms.find(textoDoPdf(pdf))).as("PDF %s", periodo).isEmpty();
            assertThat(textosDoExcel(new RelatorioExcel().gerar(rel)).stream().flatMap(t -> ConductTerms
                    .find(t).stream())).as("Excel %s", periodo).isEmpty();
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                for (PDPage p : doc.getPages()) {
                    for (var nome : p.getResources().getXObjectNames()) {
                        assertThat(p.getResources().isImageXObject(nome)).as("imagem no PDF").isFalse();
                    }
                }
            }
        }
    }

    @Test
    void doisFluxosNoMesExportamOAvisoNoLugarDosNumeros() throws IOException {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        g.cenario.fluxo("fluxo-corrigido-2026-09.pdf", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 0);
        var calculo = g.cenario.previstoRealizado.calcular(g.cenario.condominioId, "2026-09", null);
        RelatorioPrevistoRealizado rel = relatorio(calculo);

        String pdf = textoDoPdf(new RelatorioPdf().gerar(rel));
        List<String> excel = textosDoExcel(new RelatorioExcel().gerar(rel));

        assertThat(pdf).contains("Dois fluxos para 09/2026: substitua, reclassifique ou exclua um")
                .doesNotContain("446.176,89").doesNotContain("PROVISÓRIO");
        assertThat(excel).contains("Dois fluxos para 09/2026: substitua, reclassifique ou exclua um");
    }

    private static RelatorioPrevistoRealizado relatorio(CalculoPrevistoRealizado.Calculo calculo) {
        return RelatorioPrevistoRealizado.montar("Condomínio Piloto", null, calculo, "Admin Teste (admin)", GERADO_EM);
    }

    static String textoDoPdf(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper s = new PDFTextStripper();
            s.setSortByPosition(true);
            return s.getText(doc).replaceAll("[ \\t\\u00a0]+", " ");
        }
    }

    static List<String> textosDoExcel(byte[] xlsx) throws IOException {
        List<String> textos = new ArrayList<>();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            for (Sheet s : wb) {
                textos.add(s.getSheetName());
                for (Row r : s) {
                    for (Cell c : r) {
                        if (c.getCellType() == CellType.STRING) {
                            textos.add(c.getStringCellValue());
                        }
                    }
                }
            }
        }
        return textos;
    }

    private static Row linhaAbaixo(Sheet aba, String titulo, int abaixo) {
        for (Row r : aba) {
            if (titulo.equals(texto(r.getCell(0)))) {
                return aba.getRow(r.getRowNum() + abaixo);
            }
        }
        throw new AssertionError("seção ausente: " + titulo);
    }

    private static void assertIgual(Row r, int col, JsonNode esperado) {
        if (esperado == null || esperado.isNull()) {
            assertThat(texto(r.getCell(col))).as("coluna %d da linha %d sem número", col, r.getRowNum()).isEqualTo("—");
            return;
        }
        BigDecimal e = esperado.decimalValue();
        BigDecimal lido = BigDecimal.valueOf(r.getCell(col).getNumericCellValue()).setScale(e.scale(),
                RoundingMode.HALF_UP);
        assertThat(lido).as("coluna %d da linha %d", col, r.getRowNum()).isEqualByComparingTo(e);
    }

    private static String texto(Cell c) {
        return c == null || c.getCellType() != CellType.STRING ? "" : c.getStringCellValue();
    }

    private static String seq(JsonNode n, String... campos) {
        List<String> partes = new ArrayList<>();
        for (String c : campos) {
            partes.add(din(n.get(c)));
        }
        return String.join(" ", partes);
    }

    private static String din(JsonNode n) {
        return n == null || n.isNull() ? "—" : DinheiroBr.formatar(n.decimalValue());
    }

    private static String pct(JsonNode n) {
        return n == null || n.isNull() ? "—" : RelatorioPrevistoRealizado.percentual(n.decimalValue());
    }

    private static GoldenSetembro golden() {
        Optional<GoldenSetembro> g = GoldenSetembro.carregar();
        assumeTrue(g.isPresent() && GoldenSetembro.mapa().isPresent(), "golden privado ausente");
        return g.get();
    }
}
