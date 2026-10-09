package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.dto.response.budget.BudgetExtensionResponse;
import br.com.condominioauditoria.api.mapper.BudgetMapper;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.orcamento.CalculoPrevistoRealizado.Fluxo;
import br.com.condominioauditoria.api.orcamento.ColunaImpressa.DiferencaGrupo;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.FiltroDepara;
import br.com.condominioauditoria.api.orcamento.ExercicioDtos.ConferenciaColuna;
import br.com.condominioauditoria.api.orcamento.ExercicioDtos.Exercicio;
import br.com.condominioauditoria.api.orcamento.ExercicioDtos.MesDoExercicio;
import br.com.condominioauditoria.api.orcamento.ExercicioDtos.TipoExercicio;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.SituacaoMes;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.FiltroRubrica;
import br.com.condominioauditoria.api.repository.budget.BudgetLineRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import br.com.condominioauditoria.api.service.calculator.BudgetValidity;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exercícios do condomínio para o menu "Análise da PO" (RF-11.4 e RF-11.5; ADR 0005, Decisões 3 e 5). Nada é
 * gravado: a lista e a coluna impressa são montadas a cada consulta.
 *
 * <p>Exercício anterior de uma PO X: a PO confirmada cujo exercício cobre o mês anterior ao início de X (meses
 * prorrogados não contam). Sem ela, a coluna "Orçado anterior" de X vira o exercício "AAAA/AAAA (coluna impressa)",
 * só com previsto. Quando a PO anterior é confirmada depois, ela entra no lugar da coluna sem ação extra, e a coluna
 * fica só como conferência, com aviso por grupo quando os valores diferem.
 */
@Service
public class ServicoExercicios {

    private final BudgetRepository previsoes;
    private final BudgetLineRepository linhas;
    private final ConsultaPrevistoRealizado previstoRealizado;
    private final ServicoDepara depara;
    private final ServicoRubricas rubricas;

    ServicoExercicios(BudgetRepository previsoes, BudgetLineRepository linhas,
            ConsultaPrevistoRealizado previstoRealizado, ServicoDepara depara, ServicoRubricas rubricas) {
        this.previsoes = previsoes;
        this.linhas = linhas;
        this.previstoRealizado = previstoRealizado;
        this.depara = depara;
        this.rubricas = rubricas;
    }

    /** Exercícios do mais recente para o mais antigo; a coluna impressa vem logo depois da PO que a imprimiu. */
    @Transactional(readOnly = true)
    public List<Exercicio> listar(UUID condominioId) {
        List<Budget> confirmadas = confirmadas(condominioId);
        List<Fluxo> fluxos = previstoRealizado.fluxos(condominioId);
        List<Exercicio> lista = new ArrayList<>();
        for (Budget po : confirmadas) {
            List<BudgetLine> daPo = linhas.findByBudgetIdOrderByPosition(po.getId());
            // Coluna da PO seguinte que esta PO substituiu (fica só como conferência)
            Optional<Budget> seguinte = confirmadas.stream()
                    .filter(x -> anterior(confirmadas, x).filter(a -> a.getId().equals(po.getId())).isPresent())
                    .findFirst();
            String colunaSubstituida = null;
            List<String> avisos = List.of();
            if (seguinte.isPresent()) {
                Optional<ColunaImpressa> coluna = ColunaImpressa.de(seguinte.get(),
                        linhas.findByBudgetIdOrderByPosition(seguinte.get().getId()));
                if (coluna.isPresent()) {
                    colunaSubstituida = idColuna(seguinte.get());
                    avisos = coluna.get().diferencas(daPo, tolerancia(seguinte.get())).stream()
                            .map(DiferencaGrupo::texto).toList();
                }
            }
            lista.add(new Exercicio("po:" + po.getId(), TipoExercicio.PO, rotulo(po.getFiscalYearStart(),
                    po.getFiscalYearEnd()), po.getId(), po.getVersion(), po.getFiscalYearStart().toString(),
                    po.getFiscalYearEnd().toString(), BudgetMapper.toExtensionResponse(po), BudgetStructure.of(daPo).monthlyPlannedFromLines()
                            .setScale(2), meses(po, fluxos),
                    depara.listar(condominioId, po.getId(), FiltroDepara.TODAS).resumo(),
                    rubricas.listar(condominioId, po.getId(), FiltroRubrica.TODAS).resumo(), colunaSubstituida,
                    avisos));
            if (anterior(confirmadas, po).isEmpty()) {
                ColunaImpressa.de(po, daPo).ifPresent(c -> lista.add(colunaComoExercicio(po, c)));
            }
        }
        return List.copyOf(lista);
    }

    /** Ids dos exercícios ("po:" e "coluna:"), na ordem da lista, sem montar os resumos. */
    @Transactional(readOnly = true)
    public List<String> ids(UUID condominioId) {
        List<Budget> confirmadas = confirmadas(condominioId);
        List<String> ids = new ArrayList<>();
        for (Budget po : confirmadas) {
            ids.add("po:" + po.getId());
            if (anterior(confirmadas, po).isEmpty()
                    && ColunaImpressa.de(po, linhas.findByBudgetIdOrderByPosition(po.getId())).isPresent()) {
                ids.add(idColuna(po));
            }
        }
        return List.copyOf(ids);
    }

    /** Conferência da coluna "Orçado anterior" da PO (RF-11.5). 404 se a PO não está confirmada ou não tem coluna. */
    @Transactional(readOnly = true)
    public ConferenciaColuna colunaImpressa(UUID condominioId, UUID poId) {
        Budget po = previsoes.findByIdAndCondominiumId(poId, condominioId)
                .filter(p -> p.getStatus() == BudgetStatus.CONFIRMADA)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO confirmada não encontrada"));
        ColunaImpressa coluna = ColunaImpressa.de(po, linhas.findByBudgetIdOrderByPosition(po.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "A PO não tem a coluna \"Orçado anterior\""));
        Optional<Budget> anterior = anterior(confirmadas(condominioId), po);
        List<DiferencaGrupo> diferencas = anterior.map(a -> coluna.diferencas(
                linhas.findByBudgetIdOrderByPosition(a.getId()), tolerancia(po))).orElse(List.of());
        List<String> avisos = new ArrayList<>(coluna.avisos());
        diferencas.forEach(d -> avisos.add(d.texto()));
        return new ConferenciaColuna(idColuna(po), coluna.rotulo(), po.getId(), anterior.isPresent(),
                anterior.map(Budget::getId).orElse(null),
                anterior.map(a -> rotulo(a.getFiscalYearStart(), a.getFiscalYearEnd())).orElse(null),
                coluna.totalImpresso(), coluna.totalIncluiFundos(), coluna.fundos(), coluna.previstoMes(),
                coluna.grupos(), diferencas, List.copyOf(avisos));
    }

    /**
     * Exercício anterior da PO: a PO confirmada cujo exercício cobre o mês anterior ao início dela. Os exercícios não
     * se sobrepõem (RF-11.2), então há no máximo uma.
     */
    static Optional<Budget> anterior(List<Budget> confirmadas, Budget po) {
        YearMonth mes = po.getFiscalYearStart().minusMonths(1);
        return confirmadas.stream().filter(p -> !p.getId().equals(po.getId()))
                .filter(p -> BudgetValidity.of(p).filter(v -> v.covers(mes)).isPresent()).findFirst();
    }

    /** "2026/2027" (ou "2026", se o exercício está num ano só). */
    static String rotulo(YearMonth inicio, YearMonth fim) {
        return inicio.getYear() == fim.getYear() ? String.valueOf(inicio.getYear())
                : inicio.getYear() + "/" + fim.getYear();
    }

    private List<Budget> confirmadas(UUID condominioId) {
        return previsoes.findByCondominiumIdAndStatusIn(condominioId, EnumSet.of(BudgetStatus.CONFIRMADA)).stream()
                .filter(p -> p.getFiscalYearStart() != null && p.getFiscalYearEnd() != null)
                .sorted(Comparator.comparing(Budget::getFiscalYearStart).reversed()).toList();
    }

    private static Exercicio colunaComoExercicio(Budget po, ColunaImpressa c) {
        YearMonth inicio = po.getFiscalYearStart().minusMonths(12);
        YearMonth fim = po.getFiscalYearStart().minusMonths(1);
        return new Exercicio(idColuna(po), TipoExercicio.COLUNA_IMPRESSA, c.rotulo(), po.getId(), po.getVersion(),
                inicio.toString(), fim.toString(), null, c.previstoMes(), List.of(), null, null, null, c.avisos());
    }

    /** Meses do exercício e, depois deles, os prorrogados, com a situação do fluxo de cada um. */
    private static List<MesDoExercicio> meses(Budget po, List<Fluxo> fluxos) {
        List<MesDoExercicio> meses = new ArrayList<>();
        for (YearMonth m = po.getFiscalYearStart(); !m.isAfter(po.getFiscalYearEnd()); m = m.plusMonths(1)) {
            meses.add(new MesDoExercicio(m.toString(), situacao(m, fluxos), false));
        }
        BudgetValidity.extension(po).ifPresent(v -> {
            for (YearMonth m = v.start(); !m.isAfter(v.end()); m = m.plusMonths(1)) {
                meses.add(new MesDoExercicio(m.toString(), situacao(m, fluxos), true));
            }
        });
        return List.copyOf(meses);
    }

    static SituacaoMes situacao(YearMonth mes, List<Fluxo> fluxos) {
        long n = fluxos.stream().filter(f -> f.cobre(mes)).count();
        return n == 0 ? SituacaoMes.SEM_FLUXO : n == 1 ? SituacaoMes.COM_FLUXO : SituacaoMes.DOIS_FLUXOS;
    }

    private static BigDecimal tolerancia(Budget po) {
        return po.getRoundingTolerance() == null ? new BigDecimal("0.01") : po.getRoundingTolerance();
    }

    static String idColuna(Budget po) {
        return "coluna:" + po.getId();
    }
}
