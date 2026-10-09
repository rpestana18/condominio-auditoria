package br.com.condominioauditoria.api.modulo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.modulo.RegistroUso.ResumoUso;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** Exportação dos períodos ativos e do uso em Excel, com as duas abas do RF-10.6. */
class ExportacaoUsoExcelTest {

    private static final int DADOS = ExportacaoUsoExcel.LINHA_CABECALHO + 1;

    @Test
    void geraAsDuasAbasComDatasDeBrasiliaEContagensNumericas() throws Exception {
        var uso = new ResumoUso(UUID.randomUUID(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31), List.of(),
                List.of(new TotalUso("2026-11", Modulos.ASSISTENTE, FuncaoUso.CHAMADA_MCP, 12, 0, 0, 0, 0),
                        new TotalUso("2026-12", Modulos.ASSISTENTE, FuncaoUso.INDEXACAO, 40, 0, 0, 40, 380)));
        var fechado = new PeriodoAtivo(Modulos.ASSISTENTE, Instant.parse("2026-11-01T13:00:00Z"),
                Instant.parse("2026-12-15T18:30:00Z"), "ana", "=HYPERLINK(\"x\")", "bruno", null);
        var aberto = new PeriodoAtivo(Modulos.ASSISTENTE, Instant.parse("2027-01-10T13:00:00Z"), null, "carla", null,
                null, null);

        try (var planilha = new XSSFWorkbook(new ByteArrayInputStream(
                ExportacaoUsoExcel.gerar("Mio Residencial Parque", uso, List.of(fechado, aberto))))) {
            assertThat(planilha.getNumberOfSheets()).isEqualTo(2);
            Sheet periodos = planilha.getSheet("Períodos ativos");
            Sheet usoMes = planilha.getSheet("Uso por mês");
            assertThat(periodos).isNotNull();
            assertThat(usoMes).isNotNull();

            assertThat(periodos.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Mio Residencial Parque");
            assertThat(periodos.getRow(1).getCell(1).getStringCellValue()).isEqualTo("01/11/2026 a 31/12/2026");
            assertThat(periodos.getRow(ExportacaoUsoExcel.LINHA_CABECALHO).getCell(4).getStringCellValue())
                    .isEqualTo("Motivo ao ligar");

            Row primeiro = periodos.getRow(DADOS);
            assertThat(primeiro.getCell(1).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 11, 1, 10, 0));
            assertThat(primeiro.getCell(2).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 12, 15, 15, 30));
            assertThat(primeiro.getCell(3).getStringCellValue()).isEqualTo("ana");
            assertThat(primeiro.getCell(4).getCellType()).isEqualTo(CellType.STRING); // texto, nunca fórmula
            assertThat(primeiro.getCell(4).getStringCellValue()).isEqualTo("=HYPERLINK(\"x\")");
            assertThat(primeiro.getCell(5).getStringCellValue()).isEqualTo("bruno");
            assertThat(primeiro.getCell(6)).isNull(); // motivo não informado
            assertThat(periodos.getRow(DADOS + 1).getCell(2).getStringCellValue()).isEqualTo("ainda ligado");

            Row novembro = usoMes.getRow(DADOS);
            assertThat(novembro.getCell(0).getStringCellValue()).isEqualTo("2026-11");
            assertThat(novembro.getCell(2).getStringCellValue()).isEqualTo("chamada_mcp");
            assertThat(novembro.getCell(3).getNumericCellValue()).isEqualTo(12);
            Row dezembro = usoMes.getRow(DADOS + 1);
            assertThat(dezembro.getCell(6).getNumericCellValue()).isEqualTo(40);
            assertThat(dezembro.getCell(7).getNumericCellValue()).isEqualTo(380);
        }
    }

    @Test
    void semPeriodosNemUsoGeraAsAbasSoComCabecalho() throws Exception {
        var uso = new ResumoUso(UUID.randomUUID(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), List.of(),
                List.of());

        try (var planilha = new XSSFWorkbook(new ByteArrayInputStream(ExportacaoUsoExcel.gerar("C", uso, List.of())))) {
            assertThat(planilha.getSheet("Períodos ativos").getLastRowNum()).isEqualTo(ExportacaoUsoExcel.LINHA_CABECALHO);
            assertThat(planilha.getSheet("Uso por mês").getLastRowNum()).isEqualTo(ExportacaoUsoExcel.LINHA_CABECALHO);
        }
    }

    @Test
    void custoEstimadoEmDolarComTotalETextoFormatadoDoBigDecimal() throws Exception {
        var uso = new ResumoUso(UUID.randomUUID(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), List.of(),
                List.of(new TotalUso("2026-10", Modulos.ASSISTENTE, FuncaoUso.BUSCA_DOCUMENTOS, 5, 0, 0, 0, 0),
                        new TotalUso("2026-10", Modulos.ASSISTENTE, FuncaoUso.PERGUNTA, 30, 1_500_000, 100_000, 0, 0)));
        var custo = new CustoUso.CustoDoPeriodo(java.util.Map.of("2026-10|ASSISTENTE|pergunta",
                new java.math.BigDecimal("1234.50")), java.util.Map.of(), new java.math.BigDecimal("1234.50"),
                java.util.Set.of());

        try (var planilha = new XSSFWorkbook(new ByteArrayInputStream(
                ExportacaoUsoExcel.gerar("C", uso, List.of(), custo)))) {
            Sheet aba = planilha.getSheet("Uso por mês");
            assertThat(aba.getRow(ExportacaoUsoExcel.LINHA_CABECALHO).getCell(8).getStringCellValue())
                    .isEqualTo("Custo estimado (US$)");
            assertThat(aba.getRow(DADOS).getCell(8)).isNull(); // busca: sem tokens, sem custo
            assertThat(aba.getRow(DADOS + 1).getCell(8).getStringCellValue()).isEqualTo("1.234,50");
            Row total = aba.getRow(DADOS + 3);
            assertThat(total.getCell(0).getStringCellValue()).isEqualTo("Total do período");
            assertThat(total.getCell(8).getStringCellValue()).isEqualTo("1.234,50");
        }
    }

    @Test
    void semCatalogoAvisaQueOCustoEstaIndisponivel() throws Exception {
        var uso = new ResumoUso(UUID.randomUUID(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), List.of(),
                List.of(new TotalUso("2026-10", Modulos.ASSISTENTE, FuncaoUso.PERGUNTA, 1, 10, 10, 0, 0)));

        try (var planilha = new XSSFWorkbook(new ByteArrayInputStream(
                ExportacaoUsoExcel.gerar("C", uso, List.of(), null)))) {
            Sheet aba = planilha.getSheet("Uso por mês");
            assertThat(aba.getRow(DADOS + 2).getCell(0).getStringCellValue()).contains("indisponível");
        }
    }
}
