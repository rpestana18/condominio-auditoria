package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.Bloco;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.ContaBloco;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.Evidencia;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.FundoResultado;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.GrupoResultado;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.LinhaResultado;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.MesExercicio;
import br.com.condominioauditoria.api.orcamento.RelatorioPrevistoRealizado.GrupoEvidencia;
import br.com.condominioauditoria.api.orcamento.RelatorioPrevistoRealizado.Pendencia;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Excel (.xlsx) do previsto × realizado (RF-03.1.14; ADR 0004, Decisão 6), com Apache POI. Aba "Resumo" (cabeçalho,
 * "PROVISÓRIO" com a lista do que falta, totais, regra dos 20%, grupos e linhas, blocos, conferência, fundos, meses,
 * avisos) e aba "Evidência" (um lançamento por linha, com arquivo, página e hash). Dinheiro é gravado como número a
 * partir do BigDecimal do resultado, com máscara de milhar e 2 casas, e nunca por fórmula: o arquivo mostra os mesmos
 * centavos da tela. Sem gráfico.
 */
@Component
public class RelatorioExcel {

    static final String ABA_RESUMO = "Resumo";
    static final String ABA_EVIDENCIA = "Evidência";

    public byte[] gerar(RelatorioPrevistoRealizado rel) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            Estilos e = new Estilos(wb);
            resumo(wb.createSheet(ABA_RESUMO), rel, e);
            evidencia(wb.createSheet(ABA_EVIDENCIA), rel, e);
            wb.write(saida);
            return saida.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao gerar o Excel do previsto × realizado", ex);
        }
    }

    private static final class Estilos {
        final CellStyle dinheiro;
        final CellStyle percentual;
        final CellStyle titulo;
        final CellStyle cabecalho;
        final CellStyle grupo;
        final CellStyle grupoDinheiro;
        final CellStyle grupoPercentual;
        final CellStyle alerta;

        Estilos(XSSFWorkbook wb) {
            short fmtDinheiro = wb.createDataFormat().getFormat("#,##0.00");
            short fmtPercentual = wb.createDataFormat().getFormat("0.0\"%\"");
            Font negrito = wb.createFont();
            negrito.setBold(true);
            Font grande = wb.createFont();
            grande.setBold(true);
            grande.setFontHeightInPoints((short) 13);
            Font vermelho = wb.createFont();
            vermelho.setBold(true);
            vermelho.setColor(IndexedColors.DARK_RED.getIndex());
            vermelho.setFontHeightInPoints((short) 12);
            dinheiro = wb.createCellStyle();
            dinheiro.setDataFormat(fmtDinheiro);
            percentual = wb.createCellStyle();
            percentual.setDataFormat(fmtPercentual);
            titulo = wb.createCellStyle();
            titulo.setFont(grande);
            cabecalho = wb.createCellStyle();
            cabecalho.setFont(negrito);
            cabecalho.setBorderBottom(BorderStyle.THIN);
            grupo = wb.createCellStyle();
            grupo.setFont(negrito);
            grupoDinheiro = wb.createCellStyle();
            grupoDinheiro.cloneStyleFrom(dinheiro);
            grupoDinheiro.setFont(negrito);
            grupoPercentual = wb.createCellStyle();
            grupoPercentual.cloneStyleFrom(percentual);
            grupoPercentual.setFont(negrito);
            alerta = wb.createCellStyle();
            alerta.setFont(vermelho);
        }
    }

    /** Escreve linha a linha numa aba. */
    private static final class Escrita {
        final Sheet aba;
        final Estilos e;
        int linha;

        Escrita(Sheet aba, Estilos e) {
            this.aba = aba;
            this.e = e;
        }

        Row nova() {
            return aba.createRow(linha++);
        }

        void pular() {
            linha++;
        }

        Row textos(CellStyle estilo, String... valores) {
            Row r = nova();
            for (int i = 0; i < valores.length; i++) {
                texto(r, i, valores[i], estilo);
            }
            return r;
        }

        void par(String rotulo, String valor) {
            Row r = nova();
            texto(r, 0, rotulo, e.cabecalho);
            texto(r, 1, valor, null);
        }

        static void texto(Row r, int col, String v, CellStyle estilo) {
            Cell c = r.createCell(col);
            if (v != null) {
                c.setCellValue(v);
            }
            if (estilo != null) {
                c.setCellStyle(estilo);
            }
        }

        static void numero(Row r, int col, BigDecimal v, CellStyle estilo) {
            Cell c = r.createCell(col);
            if (v != null) {
                c.setCellValue(v.doubleValue());
                c.setCellStyle(estilo);
            } else {
                c.setCellValue("—");
            }
        }

        static void inteiro(Row r, int col, int v) {
            r.createCell(col).setCellValue(v);
        }
    }

    private static void resumo(Sheet aba, RelatorioPrevistoRealizado rel, Estilos e) {
        PrevistoRealizado r = rel.resultado();
        Escrita w = new Escrita(aba, e);
        w.textos(e.titulo, rel.titulo());
        if (rel.provisorio()) {
            w.textos(e.alerta, "PROVISÓRIO");
        }
        w.par("Condomínio", rel.condominio());
        w.par("PO", rel.po());
        w.par("Período", rel.periodo());
        w.par("Fundo", rel.fundo());
        w.par("Gerado em", rel.geradoEm());
        w.par("Gerado por", rel.geradoPor());
        w.par("Estado do de-para", rel.estadoDepara());
        w.par("Versão do cálculo", r.versaoCalculo());
        w.pular();

        if (rel.provisorio()) {
            w.textos(e.alerta, "PROVISÓRIO: há valores fora das linhas da PO; os números podem mudar quando estes itens"
                    + " forem tratados");
            w.textos(e.cabecalho, "Bloco", "Data", "Conta", "Nome da conta", "Histórico", "Valor", "Arquivo", "Página");
            for (Pendencia p : rel.pendencias()) {
                Evidencia l = p.lancamento();
                Row row = w.textos(null, p.bloco(), RelatorioPrevistoRealizado.data(l.data()), l.conta(),
                        l.contaNome(), l.historico());
                Escrita.numero(row, 5, l.valor(), e.dinheiro);
                Escrita.texto(row, 6, l.arquivoNome(), null);
                Escrita.inteiro(row, 7, l.pagina());
            }
            w.pular();
        }

        if (!rel.calculado()) {
            w.textos(e.cabecalho, "Sem números para este período");
            w.textos(null, r.mensagem());
            avisos(w, r);
            larguras(aba);
            return;
        }

        if (rel.comFundoCondominio()) {
            condominio(w, r);
        }
        if (rel.comFundos()) {
            fundos(w, r);
        }
        meses(w, r);
        avisos(w, r);
        w.pular();
        w.textos(null, "Os números saem do mesmo cálculo da tela e do PDF. As diferenças são fatos a verificar com a"
                + " evidência (aba \"" + ABA_EVIDENCIA + "\"); este arquivo não descreve causas.");
        larguras(aba);
    }

    private static void condominio(Escrita w, PrevistoRealizado r) {
        Estilos e = w.e;
        w.textos(e.cabecalho, "Totais do fundo Condomínio");
        totais(w, r);
        w.pular();

        if (r.regra20() != null) {
            var g = r.regra20();
            w.textos(e.cabecalho, "Regra dos 20% (Conv. 16.2)");
            w.textos(e.cabecalho, "Excesso", "% do previsto do mês", "Limite", "Linhas acima do previsto", "A realocar",
                    "Sem linha da PO", "Cenário máximo", "% cenário máximo", "Acima do limite", "Provisório");
            Row row = w.nova();
            Escrita.numero(row, 0, g.excesso(), e.dinheiro);
            Escrita.numero(row, 1, g.percentual(), e.percentual);
            Escrita.numero(row, 2, g.limite(), e.dinheiro);
            Escrita.inteiro(row, 3, g.linhasAcima());
            Escrita.numero(row, 4, g.aRealocar(), e.dinheiro);
            Escrita.numero(row, 5, g.semLinhaPo(), e.dinheiro);
            Escrita.numero(row, 6, g.cenarioMaximo(), e.dinheiro);
            Escrita.numero(row, 7, g.percentualCenarioMaximo(), e.percentual);
            Escrita.texto(row, 8, g.acimaDoLimite() ? "sim" : "não", null);
            Escrita.texto(row, 9, g.provisorio() ? "sim" : "não", null);
            w.pular();
        }

        w.textos(e.cabecalho, "Por grupo e linha da PO");
        w.textos(e.cabecalho, "Tipo", "Código", "Descrição", "Contas do fluxo", "Previsto", "Realizado", "Diferença",
                "Execução", "Observações da PO", "Página da PO");
        for (GrupoResultado g : r.grupos()) {
            Row row = w.textos(e.grupo, "Grupo", g.codigo(), g.descricao(), "");
            Escrita.numero(row, 4, g.previsto(), e.grupoDinheiro);
            Escrita.numero(row, 5, g.realizado(), e.grupoDinheiro);
            Escrita.numero(row, 6, g.diferenca(), e.grupoDinheiro);
            Escrita.numero(row, 7, g.execucao(), e.grupoPercentual);
            for (LinhaResultado l : g.linhas()) {
                Row lr = w.textos(null, "Linha", l.codigo(), l.descricao(), String.join(" ", l.contasFluxo()));
                Escrita.numero(lr, 4, l.previsto(), e.dinheiro);
                Escrita.numero(lr, 5, l.realizado(), e.dinheiro);
                Escrita.numero(lr, 6, l.diferenca(), e.dinheiro);
                Escrita.numero(lr, 7, l.execucao(), e.percentual);
                Escrita.texto(lr, 8, l.observacoes(), null);
                Escrita.inteiro(lr, 9, l.pagina());
            }
        }
        w.pular();

        w.textos(e.cabecalho, "Blocos à parte (fora das linhas da PO)");
        w.textos(e.cabecalho, "Bloco", "Conta", "Nome", "Detalhe", "Lançamentos", "Valor");
        bloco(w, "Ajustes (não são despesa)", r.ajustes());
        bloco(w, "A realocar", r.aRealocar());
        bloco(w, "Sem linha da PO", r.semLinhaPo());
        w.pular();

        var c = r.conferencia();
        w.textos(e.cabecalho, "Conferência com o fluxo");
        w.textos(e.cabecalho, "Débitos do fundo", "Lançamentos", "Despesa realizada", "Ajustes", "Transferências",
                "Confere");
        Row cr = w.nova();
        Escrita.numero(cr, 0, c.debitosDoFundo(), e.dinheiro);
        Escrita.inteiro(cr, 1, c.lancamentos());
        Escrita.numero(cr, 2, c.despesaRealizada(), e.dinheiro);
        Escrita.numero(cr, 3, c.ajustes(), e.dinheiro);
        Escrita.numero(cr, 4, c.transferencias(), e.dinheiro);
        Escrita.texto(cr, 5, c.confere() ? "sim" : "não", null);
        w.pular();

    }

    private static void fundos(Escrita w, PrevistoRealizado r) {
        Estilos e = w.e;
        w.textos(e.cabecalho, "Fundos");
        w.textos(e.cabecalho, "Fundo", "Linha da PO", "Situação", "Previsto", "Arrecadado", "Diferença", "Execução",
                "Créditos", "Débitos");
        for (FundoResultado f : r.fundos()) {
            Row fr = w.textos(null, f.fundo() == null ? "—" : f.fundo(), f.linhaCodigo() == null ? "—" : f.linhaCodigo(),
                    switch (f.situacao()) {
                        case COMPARADO -> "arrecadação × previsto";
                        case SEM_PREVISTO_NA_PO -> "sem previsto na PO";
                        case LINHA_SEM_FUNDO -> "linha sem fundo ligado";
                        case REPROCESSAR_FLUXO -> "reprocesse o fluxo";
                    });
            Escrita.numero(fr, 3, f.previsto(), e.dinheiro);
            Escrita.numero(fr, 4, f.arrecadado(), e.dinheiro);
            Escrita.numero(fr, 5, f.diferenca(), e.dinheiro);
            Escrita.numero(fr, 6, f.execucao(), e.percentual);
            Escrita.numero(fr, 7, f.creditos(), e.dinheiro);
            Escrita.numero(fr, 8, f.debitos(), e.dinheiro);
        }
        w.pular();

    }

    private static void meses(Escrita w, PrevistoRealizado r) {
        Estilos e = w.e;
        if (r.meses().size() > 1) {
            w.textos(e.cabecalho, "Meses do exercício");
            w.textos(e.cabecalho, "Mês", "Situação", "Previsto", "Despesa realizada", "Excesso", "% excesso");
            for (MesExercicio m : r.meses()) {
                Row mr = w.textos(null, RelatorioPrevistoRealizado.mes(m.mes()), switch (m.situacao()) {
                    case COM_FLUXO -> "com fluxo";
                    case SEM_FLUXO -> "sem fluxo carregado";
                    case DOIS_FLUXOS -> "dois fluxos";
                });
                Escrita.numero(mr, 2, m.previsto(), e.dinheiro);
                Escrita.numero(mr, 3, m.despesaRealizada(), e.dinheiro);
                Escrita.numero(mr, 4, m.excesso(), e.dinheiro);
                Escrita.numero(mr, 5, m.percentualExcesso(), e.percentual);
            }
            w.pular();
        }
    }

    private static void totais(Escrita w, PrevistoRealizado r) {
        Estilos e = w.e;
        w.textos(e.cabecalho, "Previsto", "Despesa realizada", "Em linhas da PO", "Diferença", "Execução",
                "Previsto do mês", "Previsto do exercício (referência)");
        Row row = w.nova();
        var t = r.totais();
        Escrita.numero(row, 0, t.previsto(), e.dinheiro);
        Escrita.numero(row, 1, t.despesaRealizada(), e.dinheiro);
        Escrita.numero(row, 2, t.emLinhas(), e.dinheiro);
        Escrita.numero(row, 3, t.diferenca(), e.dinheiro);
        Escrita.numero(row, 4, t.execucao(), e.percentual);
        Escrita.numero(row, 5, t.previstoMes(), e.dinheiro);
        Escrita.numero(row, 6, t.previstoExercicio(), e.dinheiro);
    }

    private static void bloco(Escrita w, String nome, Bloco b) {
        Row row = w.textos(w.e.grupo, nome, "", "", "");
        Escrita.inteiro(row, 4, b.lancamentos());
        Escrita.numero(row, 5, b.total(), w.e.grupoDinheiro);
        for (ContaBloco c : b.contas()) {
            Row cr = w.textos(null, "", c.conta() == null ? "—" : c.conta(), c.nome(), c.detalhe());
            Escrita.inteiro(cr, 4, c.lancamentos());
            Escrita.numero(cr, 5, c.valor(), w.e.dinheiro);
        }
    }

    private static void avisos(Escrita w, PrevistoRealizado r) {
        if (r.avisos().isEmpty()) {
            return;
        }
        w.textos(w.e.cabecalho, "Avisos");
        r.avisos().forEach(a -> w.textos(null, a.texto()));
    }

    private static void evidencia(Sheet aba, RelatorioPrevistoRealizado rel, Estilos e) {
        Escrita w = new Escrita(aba, e);
        w.textos(e.cabecalho, "Número (linha da PO ou bloco)", "Data", "Conta", "Nome da conta", "Histórico",
                "Fornecedor", "Documento", "Valor", "Fundo", "Arquivo", "Página", "Ordem", "Hash (SHA-256)",
                "Realocação");
        for (GrupoEvidencia g : rel.evidencias()) {
            for (Evidencia l : g.lancamentos()) {
                Row row = w.textos(null, g.rotulo(), RelatorioPrevistoRealizado.data(l.data()), l.conta(),
                        l.contaNome(), l.historico(), l.fornecedor(), l.documento());
                Escrita.numero(row, 7, l.valor(), e.dinheiro);
                Escrita.texto(row, 8, l.fundo(), null);
                Escrita.texto(row, 9, l.arquivoNome(), null);
                Escrita.inteiro(row, 10, l.pagina());
                Escrita.inteiro(row, 11, l.ordem());
                Escrita.texto(row, 12, l.sha256(), null);
                Escrita.texto(row, 13, l.realocacao(), null);
            }
        }
        aba.createFreezePane(0, 1);
        larguras(aba);
    }

    private static void larguras(Sheet aba) {
        int[] largura = {28, 12, 34, 22, 40, 16, 16, 14, 30, 30, 10, 8, 20, 30};
        for (int i = 0; i < largura.length; i++) {
            aba.setColumnWidth(i, largura[i] * 256);
        }
    }

    /** Usado nos testes: rótulos dos números exportados. */
    static List<String> abas() {
        return List.of(ABA_RESUMO, ABA_EVIDENCIA);
    }
}
