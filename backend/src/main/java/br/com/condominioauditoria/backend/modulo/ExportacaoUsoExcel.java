package br.com.condominioauditoria.backend.modulo;

import br.com.condominioauditoria.backend.modulo.RegistroUso.ResumoUso;
import br.com.condominioauditoria.backend.orcamento.DinheiroBr;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Períodos ativos e uso do período em Excel (.xlsx), RF-10.6 e RF-09.7, com o Apache POI (docs/tecnologias.md, T11).
 * Duas abas: "Períodos ativos" e "Uso por mês". Datas e horas em células de data, no horário de Brasília; contagens em
 * células numéricas. Texto livre (ex.: motivo) vai como texto, nunca como fórmula. Sem valores de cobrança nesta fase.
 *
 * Custo estimado em US$ (RF-09.7; ADR 0003, Decisão 4): coluna por mês e função e uma linha de total, calculados por
 * {@link CustoUso} (tokens × preço do catálogo do rag, 2 casas só nos totais). Célula vazia = sem tokens; "sem preço"
 * = tokens de modelo fora do catálogo. O valor vai como texto no padrão brasileiro (1.234,56), formatado do
 * BigDecimal, nunca como número de ponto flutuante. Sem catálogo (rag fora do ar), a planilha sai sem custo e com aviso.
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

    static final String COLUNA_CUSTO = "Custo estimado (US$)";

    public static byte[] gerar(String nomeCondominio, ResumoUso uso, List<PeriodoAtivo> periodos) {
        return gerar(nomeCondominio, uso, periodos, null);
    }

    /** custo nulo = catálogo de preços indisponível. */
    public static byte[] gerar(String nomeCondominio, ResumoUso uso, List<PeriodoAtivo> periodos,
            CustoUso.CustoDoPeriodo custo) {
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
                    "Quantidade", "Tokens de entrada", "Tokens de saída", "Arquivos", "Páginas", COLUNA_CUSTO),
                    new int[] {10, 14, 18, 12, 18, 16, 10, 10, 20});
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
                if (custo != null && (t.tokensEntrada() > 0 || t.tokensSaida() > 0)) {
                    valorCusto(linha, 8, custo.doMes(t), estilos);
                }
            }
            boolean temTokens = uso.porMes().stream().anyMatch(t -> t.tokensEntrada() > 0 || t.tokensSaida() > 0);
            if (custo != null || temTokens) {
                n++;
                if (custo == null) {
                    texto(abaUso.createRow(n), 0, "Custo estimado indisponível: o catálogo de preços do serviço rag"
                            + " não respondeu.");
                } else {
                    Row total = abaUso.createRow(n++);
                    celula(total, 0, "Total do período", estilos.negrito);
                    valorCusto(total, 8, custo.total(), estilos);
                    texto(abaUso.createRow(n++), 0, "Custo estimado em US$: tokens × preço por milhão de tokens do"
                            + " catálogo de IA, arredondado a 2 casas só nos totais. Não é valor de cobrança.");
                    if (!custo.modelosSemPreco().isEmpty()) {
                        texto(abaUso.createRow(n), 0, "Sem preço no catálogo (custo não estimado): "
                                + String.join(", ", custo.modelosSemPreco()));
                    }
                }
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

    private static void valorCusto(Row linha, int coluna, BigDecimal valor, Estilos estilos) {
        if (valor == null) {
            texto(linha, coluna, "sem preço");
            return;
        }
        // Texto formatado a partir do BigDecimal (1.234,56), sem passar por ponto flutuante
        Cell c = linha.createCell(coluna);
        c.setCellValue(DinheiroBr.formatar(valor));
        c.setCellStyle(estilos.direita);
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
        final CellStyle direita;

        Estilos(XSSFWorkbook planilha) {
            Font fonte = planilha.createFont();
            fonte.setBold(true);
            negrito = planilha.createCellStyle();
            negrito.setFont(fonte);
            dataHora = planilha.createCellStyle();
            dataHora.setDataFormat(planilha.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy hh:mm:ss"));
            direita = planilha.createCellStyle();
            direita.setAlignment(HorizontalAlignment.RIGHT);
        }
    }
}
