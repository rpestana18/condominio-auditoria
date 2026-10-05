package br.com.condominioauditoria.backend.modulo;

import br.com.condominioauditoria.backend.modulo.RegistroUso.ResumoUso;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Períodos ativos e uso do período em Excel (.xlsx), RF-10.6 e RF-09.7, com o Apache POI (docs/tecnologias.md, T11).
 * Duas abas: "Períodos ativos" e "Uso por mês". Datas e horas em células de data, no horário de Brasília; contagens em
 * células numéricas. Texto livre (ex.: motivo) vai como texto, nunca como fórmula. Sem valores de cobrança nesta fase.
 */
public final class ExportacaoUsoExcel {

    public static final String TIPO = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String ABA_PERIODOS = "Períodos ativos";
    static final String ABA_USO = "Uso por mês";
    /** Linha (base 0) do cabeçalho da tabela: antes vêm condomínio, período e uma linha em branco. */
    static final int LINHA_CABECALHO = 3;

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ExportacaoUsoExcel() {
    }

    public static byte[] gerar(String nomeCondominio, ResumoUso uso, List<PeriodoAtivo> periodos) {
        try (var planilha = new XSSFWorkbook(); var saida = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(planilha);
            String periodo = DATA.format(uso.inicio()) + " a " + DATA.format(uso.fim());

            Sheet abaPeriodos = aba(planilha, ABA_PERIODOS, nomeCondominio, periodo, estilos, List.of("Módulo", "Início",
                    "Fim", "Ligado por", "Motivo ao ligar", "Desligado por", "Motivo ao desligar"),
                    new int[] {14, 20, 20, 22, 40, 22, 40});
            int n = LINHA_CABECALHO + 1;
            for (PeriodoAtivo p : periodos) {
                Row linha = abaPeriodos.createRow(n++);
                texto(linha, 0, p.modulo());
                dataHora(linha, 1, p.inicio(), estilos);
                if (p.fim() == null) {
                    texto(linha, 2, "ainda ligado");
                } else {
                    dataHora(linha, 2, p.fim(), estilos);
                }
                texto(linha, 3, p.ligadoPor());
                texto(linha, 4, p.motivoLigar());
                texto(linha, 5, p.desligadoPor());
                texto(linha, 6, p.motivoDesligar());
            }

            Sheet abaUso = aba(planilha, ABA_USO, nomeCondominio, periodo, estilos, List.of("Mês", "Módulo", "Função",
                    "Quantidade", "Tokens de entrada", "Tokens de saída", "Arquivos", "Páginas"),
                    new int[] {10, 14, 18, 12, 18, 16, 10, 10});
            n = LINHA_CABECALHO + 1;
            for (TotalUso t : uso.porMes()) {
                Row linha = abaUso.createRow(n++);
                texto(linha, 0, t.mes());
                texto(linha, 1, t.modulo());
                texto(linha, 2, t.funcao().codigo());
                linha.createCell(3).setCellValue(t.quantidade());
                linha.createCell(4).setCellValue(t.tokensEntrada());
                linha.createCell(5).setCellValue(t.tokensSaida());
                linha.createCell(6).setCellValue(t.arquivos());
                linha.createCell(7).setCellValue(t.paginas());
            }

            planilha.write(saida);
            return saida.toByteArray();
        } catch (IOException erro) {
            throw new UncheckedIOException("Falha ao gerar o Excel de uso", erro);
        }
    }

    private static Sheet aba(XSSFWorkbook planilha, String nome, String condominio, String periodo, Estilos estilos,
            List<String> colunas, int[] larguras) {
        Sheet aba = planilha.createSheet(nome);
        Row r0 = aba.createRow(0);
        celula(r0, 0, "Condomínio", estilos.negrito);
        texto(r0, 1, condominio);
        Row r1 = aba.createRow(1);
        celula(r1, 0, "Período", estilos.negrito);
        texto(r1, 1, periodo);
        Row cabecalho = aba.createRow(LINHA_CABECALHO);
        for (int i = 0; i < colunas.size(); i++) {
            celula(cabecalho, i, colunas.get(i), estilos.negrito);
            aba.setColumnWidth(i, larguras[i] * 256);
        }
        aba.createFreezePane(0, LINHA_CABECALHO + 1);
        return aba;
    }

    private static void texto(Row linha, int coluna, String valor) {
        if (valor != null) {
            linha.createCell(coluna).setCellValue(valor);
        }
    }

    private static void celula(Row linha, int coluna, String valor, CellStyle estilo) {
        Cell c = linha.createCell(coluna);
        c.setCellValue(valor);
        c.setCellStyle(estilo);
    }

    private static void dataHora(Row linha, int coluna, Instant instante, Estilos estilos) {
        if (instante == null) {
            return;
        }
        Cell c = linha.createCell(coluna);
        c.setCellValue(LocalDateTime.ofInstant(instante, RegistroUso.FUSO));
        c.setCellStyle(estilos.dataHora);
    }

    private static final class Estilos {
        final CellStyle negrito;
        final CellStyle dataHora;

        Estilos(XSSFWorkbook planilha) {
            Font fonte = planilha.createFont();
            fonte.setBold(true);
            negrito = planilha.createCellStyle();
            negrito.setFont(fonte);
            dataHora = planilha.createCellStyle();
            dataHora.setDataFormat(planilha.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy hh:mm:ss"));
        }
    }
}
