package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.orcamento.CalculoPrevistoRealizado.Calculo;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.ContaBloco;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.FluxoUsado;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.LinhaResultado;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.Situacao;
import br.com.condominioauditoria.api.service.audit.FindingSyncService.AssessedFinding;
import br.com.condominioauditoria.api.service.audit.FindingSyncService.Evidence;
import br.com.condominioauditoria.api.service.audit.rule.MonthlyOverrunRule;
import br.com.condominioauditoria.api.service.audit.rule.UnmappedAccountRule;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Achados de um mês a partir do resultado do {@link CalculoPrevistoRealizado} (o mesmo da tela): regra dos 20%
 * (crítico, RF-03.1.11) e conta sem linha da PO (atenção, RF-03.1.6 e RF-02.7). Função pura. Os textos descrevem o
 * fato, a regra e o que verificar; nunca a causa (RF-03.1.12).
 *
 * @param regras regras avaliadas neste mês: só os achados delas podem mudar de estado (a regra dos 20% não é avaliada
 *     sem o limite cadastrado, e aí os achados dela ficam como estão)
 */
public record AchadosDoMes(YearMonth mes, Set<String> regras, List<AssessedFinding> apurados) {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final String ALVO_FUNDO_CONDOMINIO = "fundo-condominio";

    /** Vazio (nenhuma regra avaliada) quando o mês não tem números: sem fluxo, dois fluxos, sem PO etc. */
    public static AchadosDoMes apurar(YearMonth mes, Calculo calculo) {
        PrevistoRealizado r = calculo.resultado();
        if (r.situacao() != Situacao.CALCULADO) {
            return new AchadosDoMes(mes, Set.of(), List.of());
        }
        Set<String> regras = new LinkedHashSet<>();
        List<AssessedFinding> apurados = new ArrayList<>();
        regras.add(UnmappedAccountRule.CODE);
        contasSemLinha(mes, calculo, apurados);
        if (r.regra20() != null) {
            regras.add(MonthlyOverrunRule.CODE);
            if (r.regra20().acimaDoLimite()) {
                apurados.add(excesso(mes, r));
            }
        }
        return new AchadosDoMes(mes, Set.copyOf(regras), List.copyOf(apurados));
    }

    private static void contasSemLinha(YearMonth mes, Calculo calculo, List<AssessedFinding> apurados) {
        PrevistoRealizado r = calculo.resultado();
        List<PrevistoRealizado.Evidencia> lancamentos = calculo.evidencias()
                .getOrDefault(CalculoPrevistoRealizado.ALVO_SEM_LINHA_PO, List.of());
        for (ContaBloco c : r.semLinhaPo().contas()) {
            List<Evidence> provas = lancamentos.stream().filter(ev -> Objects.equals(ev.conta(), c.conta()))
                    .map(ev -> new Evidence(ev.arquivoId(), ev.sha256(), ev.pagina(), "Lançamento de "
                            + DATA.format(ev.data()) + (ev.conta() == null ? ", sem conta" : ", conta " + ev.conta())
                            + ", R$ " + DinheiroBr.formatar(ev.valor()) + " (ordem " + ev.ordem() + "): "
                            + ev.historico(), null))
                    .toList();
            String conta = c.conta() == null ? "Lançamentos sem conta do fluxo"
                    : "Conta " + c.conta() + (c.nome() == null || c.nome().isBlank() ? "" : " " + c.nome());
            String descricao = conta + " no fundo Condomínio em " + CalculoPrevistoRealizado.mmaaaa(mes)
                    + ": R$ " + DinheiroBr.formatar(c.valor()) + " em " + c.lancamentos()
                    + (c.lancamentos() == 1 ? " lançamento" : " lançamentos") + " sem linha da PO ("
                    + (c.detalhe() == null ? "sem de-para" : c.detalhe()) + "). Os valores entram na despesa"
                    + " realizada e em nenhuma linha da PO; verificar o de-para da conta.";
            apurados.add(new AssessedFinding(UnmappedAccountRule.CODE, UnmappedAccountRule.VERSION,
                    UnmappedAccountRule.SEVERITY, UnmappedAccountRule.target(c.conta()), descricao, provas));
        }
    }

    private static AssessedFinding excesso(YearMonth mes, PrevistoRealizado r) {
        var regra = r.regra20();
        Map<UUID, LinhaResultado> linhas = r.grupos().stream().flatMap(g -> g.linhas().stream())
                .collect(Collectors.toMap(LinhaResultado::linhaId, Function.identity()));
        List<Evidence> provas = new ArrayList<>();
        for (var l : regra.linhas()) {
            LinhaResultado lr = linhas.get(l.linhaId());
            provas.add(new Evidence(r.po().arquivoId(), r.po().sha256(), lr == null ? null : lr.pagina(),
                    "PO, linha " + l.codigo() + " " + l.descricao() + ": previsto R$ "
                            + (lr == null ? "—" : DinheiroBr.formatar(lr.previsto())) + ", realizado R$ "
                            + (lr == null ? "—" : DinheiroBr.formatar(lr.realizado())) + ", acima do previsto R$ "
                            + DinheiroBr.formatar(l.excesso()), l.linhaId()));
        }
        for (FluxoUsado f : r.meses().isEmpty() ? List.<FluxoUsado>of() : r.meses().getFirst().fluxos()) {
            provas.add(new Evidence(f.arquivoId(), f.sha256(), null, "Fluxo de caixa " + f.nome() + " ("
                    + CalculoPrevistoRealizado.mmaaaa(mes) + ")", null));
        }
        String descricao = "excesso de " + percentual(regra.percentual()) + " do previsto do mês; a Conv. 16.2 exige"
                + " aprovação em AGE para o excedente; verificar ata. " + CalculoPrevistoRealizado.mmaaaa(mes)
                + ", fundo Condomínio: excesso R$ " + DinheiroBr.formatar(regra.excesso()) + " somado em "
                + regra.linhasAcima() + (regra.linhasAcima() == 1 ? " linha" : " linhas") + " acima do previsto;"
                + " previsto do mês R$ " + DinheiroBr.formatar(regra.previstoMes()) + "; limite de "
                + percentual(regra.limitePercentual()) + ": R$ " + DinheiroBr.formatar(regra.limite()) + ".";
        return new AssessedFinding(MonthlyOverrunRule.CODE, MonthlyOverrunRule.VERSION, MonthlyOverrunRule.SEVERITY,
                ALVO_FUNDO_CONDOMINIO, descricao, provas);
    }

    static String percentual(BigDecimal v) {
        BigDecimal p = v.stripTrailingZeros();
        if (p.scale() < 0) {
            p = p.setScale(0);
        }
        return p.toPlainString().replace('.', ',') + "%";
    }
}
