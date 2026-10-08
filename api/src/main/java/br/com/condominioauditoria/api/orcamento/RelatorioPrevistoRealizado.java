package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.orcamento.CalculoPrevistoRealizado.Calculo;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.Evidencia;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.FundoResultado;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.GrupoResultado;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.LinhaResultado;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.Situacao;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * O que a exportação em PDF e em Excel mostra (RF-03.1.14), montado do <b>mesmo</b> {@link Calculo} da tela: nenhum
 * número é recalculado aqui, só formatado. Cabeçalho (condomínio, PO com arquivo, versão, hash e exercício, período,
 * data e hora, quem gerou, estado do de-para), marca "PROVISÓRIO" com a lista do que falta e a evidência por linha.
 * Função pura: o instante de geração entra como parâmetro.
 */
public record RelatorioPrevistoRealizado(String condominio, String periodo, String fundo, String geradoEm,
        String geradoPor, String estadoDepara, PrevistoRealizado resultado, boolean calculado, boolean provisorio,
        List<Pendencia> pendencias, List<GrupoEvidencia> evidencias) {

    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(FUSO);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Item que deixa o resultado provisório: lançamento a realocar ou sem linha da PO. */
    public record Pendencia(String bloco, Evidencia lancamento) {
    }

    /** Lançamentos que compõem um número (aba "Evidência" do Excel). */
    public record GrupoEvidencia(String alvo, String rotulo, List<Evidencia> lancamentos) {
    }

    public static RelatorioPrevistoRealizado montar(String condominio, String fundo, Calculo calculo, String geradoPor,
            Instant geradoEm) {
        PrevistoRealizado r = calculo.resultado();
        boolean calculado = r.situacao() == Situacao.CALCULADO;
        List<Pendencia> pendencias = new ArrayList<>();
        if (calculado) {
            calculo.evidencias().getOrDefault(CalculoPrevistoRealizado.ALVO_A_REALOCAR, List.of())
                    .forEach(ev -> pendencias.add(new Pendencia("A realocar", ev)));
            calculo.evidencias().getOrDefault(CalculoPrevistoRealizado.ALVO_SEM_LINHA_PO, List.of())
                    .forEach(ev -> pendencias.add(new Pendencia("Sem linha da PO", ev)));
        }
        List<GrupoEvidencia> evidencias = new ArrayList<>();
        if (calculado) {
            for (GrupoResultado g : r.grupos()) {
                for (LinhaResultado l : g.linhas()) {
                    List<Evidencia> ev = calculo.evidencias().get(CalculoPrevistoRealizado.alvoLinha(l.linhaId()));
                    if (ev != null && !ev.isEmpty()) {
                        evidencias.add(new GrupoEvidencia(CalculoPrevistoRealizado.alvoLinha(l.linhaId()),
                                l.codigo() + " " + l.descricao(), ev));
                    }
                }
            }
            adicionar(evidencias, calculo, CalculoPrevistoRealizado.ALVO_AJUSTES, "Ajustes (não são despesa)");
            adicionar(evidencias, calculo, CalculoPrevistoRealizado.ALVO_A_REALOCAR, "A realocar");
            adicionar(evidencias, calculo, CalculoPrevistoRealizado.ALVO_SEM_LINHA_PO, "Sem linha da PO");
            adicionar(evidencias, calculo, CalculoPrevistoRealizado.ALVO_TRANSFERENCIAS, "Transferências (fora)");
            for (FundoResultado f : r.fundos()) {
                if (f.fundoId() != null && f.linhaCodigo() != null) {
                    adicionar(evidencias, calculo, CalculoPrevistoRealizado.alvoFundo(f.fundoId()),
                            "Arrecadação " + f.fundo() + " (" + f.linhaCodigo() + ")");
                }
            }
        }
        String depara = r.depara() == null ? "—" : r.depara().confirmadas() + " de " + r.depara().contas()
                + " contas confirmadas" + (r.depara().semDeparaConfirmado() == 0 ? ""
                : "; " + r.depara().semDeparaConfirmado() + " sem de-para confirmado");
        return new RelatorioPrevistoRealizado(condominio, periodo(r), fundo == null ? "Todos" : fundo, DATA_HORA.format(geradoEm), geradoPor, depara, r, calculado, calculado && r.provisorio(),
                List.copyOf(pendencias), List.copyOf(evidencias));
    }

    private static void adicionar(List<GrupoEvidencia> lista, Calculo c, String alvo, String rotulo) {
        List<Evidencia> ev = c.evidencias().get(alvo);
        if (ev != null && !ev.isEmpty()) {
            lista.add(new GrupoEvidencia(alvo, rotulo, ev));
        }
    }

    private static String periodo(PrevistoRealizado r) {
        if (!"acumulado".equals(r.periodo())) {
            return CalculoPrevistoRealizado.mmaaaa(YearMonth.parse(r.periodo()));
        }
        String somados = r.mesesSomados().isEmpty() ? "nenhum"
                : CalculoPrevistoRealizado.listaDeMeses(r.mesesSomados());
        return "Acumulado do exercício (meses somados: " + somados + ")";
    }

    /** Há números do fundo Condomínio (falso quando o filtro é outro fundo). */
    public boolean comFundoCondominio() {
        return calculado && resultado.totais() != null;
    }

    /** Há painel de fundos (falso quando o filtro é só o fundo Condomínio). */
    public boolean comFundos() {
        return calculado && !resultado.fundos().isEmpty();
    }

    /** Texto da PO no cabeçalho: arquivo, versão, hash e exercício. */
    public String po() {
        var p = resultado.po();
        if (p == null) {
            return "—";
        }
        return Objects.requireNonNullElse(p.arquivoNome(), "arquivo " + p.arquivoId()) + "; versão "
                + (p.versao() == null ? "—" : p.versao()) + "; exercício " + mes(p.exercicioInicio()) + " a "
                + mes(p.exercicioFim()) + "; hash " + p.sha256();
    }

    /** Título do documento, sem gráfico (decisão do usuário, ADR 0004, pergunta 7). */
    public String titulo() {
        return "Previsto × realizado – " + condominio;
    }

    // Formatação: os mesmos números do JSON, só com a máscara brasileira

    public static String dinheiro(BigDecimal v) {
        return v == null ? "—" : DinheiroBr.formatar(v);
    }

    public static String percentual(BigDecimal v) {
        return v == null ? "—" : v.toPlainString().replace('.', ',') + "%";
    }

    public static String data(LocalDate d) {
        return d == null ? "—" : DATA.format(d);
    }

    public static String mes(String aaaaMm) {
        return aaaaMm == null ? "—" : CalculoPrevistoRealizado.mmaaaa(YearMonth.parse(aaaaMm));
    }
}
