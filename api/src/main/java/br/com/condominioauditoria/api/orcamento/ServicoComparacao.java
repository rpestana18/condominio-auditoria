package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.model.audit.Finding;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetFundLink;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.model.enums.FindingStatus;
import br.com.condominioauditoria.api.orcamento.ComparacaoExercicios.Entrada;
import br.com.condominioauditoria.api.orcamento.ComparacaoExercicios.Filtro;
import br.com.condominioauditoria.api.orcamento.ComparacaoExercicios.Resultado;
import br.com.condominioauditoria.api.orcamento.ComparacaoExercicios.RubricaDaLinha;
import br.com.condominioauditoria.api.orcamento.ExercicioDtos.TipoExercicio;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.Situacao;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.SituacaoMes;
import br.com.condominioauditoria.api.repository.audit.FindingRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetFundLinkRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import java.time.Month;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Monta a entrada da {@link ComparacaoExercicios} a partir do banco (RF-11.6; ADR 0005, Decisão 2): um cálculo do
 * acumulado por exercício com PO, pela mesma consulta da tela de previsto × realizado, e, em "mesmos meses", um
 * cálculo por mês comparado. Nada é gravado.
 */
@Service
public class ServicoComparacao {

    private final CondominiumRepository condominios;
    private final BudgetRepository previsoes;
    private final BudgetLineRepository linhas;
    private final BudgetFundLinkRepository poFundos;
    private final RubricaRepository rubricas;
    private final LinhaRubricaRepository linhasRubrica;
    private final FindingRepository achados;
    private final ConsultaPrevistoRealizado previstoRealizado;
    private final ServicoExercicios exercicios;

    ServicoComparacao(CondominiumRepository condominios, BudgetRepository previsoes,
            BudgetLineRepository linhas, BudgetFundLinkRepository poFundos, RubricaRepository rubricas,
            LinhaRubricaRepository linhasRubrica, FindingRepository achados,
            ConsultaPrevistoRealizado previstoRealizado, ServicoExercicios exercicios) {
        this.condominios = condominios;
        this.previsoes = previsoes;
        this.linhas = linhas;
        this.poFundos = poFundos;
        this.rubricas = rubricas;
        this.linhasRubrica = linhasRubrica;
        this.achados = achados;
        this.previstoRealizado = previstoRealizado;
        this.exercicios = exercicios;
    }

    /** Um exercício escolhido: a PO e se é a coluna impressa dela. */
    private record Escolhido(String id, Budget po, boolean coluna) {

        YearMonth inicio() {
            return coluna ? po.getFiscalYearStart().minusMonths(12) : po.getFiscalYearStart();
        }

        YearMonth fim() {
            return coluna ? po.getFiscalYearStart().minusMonths(1) : po.getFiscalYearEnd();
        }
    }

    /**
     * {@code ids}: "po:&lt;uuid&gt;" (ou só o uuid) e "coluna:&lt;uuid&gt;"; vazio = os dois exercícios mais recentes.
     * {@code fundoId} nulo = todos.
     */
    @Transactional(readOnly = true)
    public Resultado comparar(UUID condominioId, List<String> ids, UUID fundoId, boolean mesmosMeses) {
        Condominium condominio = condominios.findById(condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        if (fundoId != null) {
            previstoRealizado.fundoDoFiltro(condominioId, fundoId);
        }
        List<String> pedidos = ids == null ? List.of() : ids.stream().map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new)).stream().toList();
        if (pedidos.isEmpty()) {
            pedidos = exercicios.ids(condominioId).stream().limit(2).toList();
        }
        List<Escolhido> escolhidos = pedidos.stream().map(id -> escolher(condominioId, id))
                .sorted(Comparator.comparing(Escolhido::inicio).reversed()).toList();
        if (escolhidos.size() < 2) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Escolha dois ou mais exercícios para comparar");
        }

        Map<String, PrevistoRealizado> acumulados = new java.util.HashMap<>();
        for (Escolhido x : escolhidos) {
            if (!x.coluna()) {
                acumulados.put(x.id(), previstoRealizado.calcular(condominioId, "acumulado", x.po().getId()).resultado());
            }
        }
        Set<Month> comuns = mesmosMeses ? ComparacaoExercicios.mesmosMeses(acumulados.values()) : Set.of();
        List<Finding> todosAchados = achados.findByCondominiumIdOrderByReferenceMonthDescCreatedAtAsc(condominioId);
        Map<UUID, RubricaDaLinha> catalogo = rubricas.findByCondominioIdOrderByNome(condominioId).stream()
                .collect(Collectors.toMap(Rubrica::getId,
                        r -> new RubricaDaLinha(r.getId(), r.getNome(), r.getGrupoCodigo())));

        List<Entrada> entradas = new ArrayList<>();
        for (Escolhido x : escolhidos) {
            UUID poId = x.po().getId();
            PrevistoRealizado acumulado = acumulados.get(x.id());
            List<PrevistoRealizado> periodo = new ArrayList<>();
            List<String> meses = new ArrayList<>();
            if (acumulado != null && mesmosMeses) {
                acumulado.meses().stream()
                        .filter(m -> !m.prorrogado() && m.situacao() == SituacaoMes.COM_FLUXO
                                && comuns.contains(YearMonth.parse(m.mes()).getMonth()))
                        .forEach(m -> {
                            PrevistoRealizado r = previstoRealizado.calcular(condominioId, m.mes(), poId).resultado();
                            if (r.situacao() == Situacao.CALCULADO) {
                                periodo.add(r);
                                meses.add(m.mes());
                            }
                        });
            } else if (acumulado != null && acumulado.situacao() == Situacao.CALCULADO) {
                periodo.add(acumulado);
                meses.addAll(acumulado.mesesSomados());
            }
            Map<UUID, RubricaDaLinha> daLinha = linhasRubrica.findByPrevisaoId(poId).stream()
                    .filter(l -> l.getEstado() == EstadoRubrica.CONFIRMADO && catalogo.containsKey(l.getRubricaId()))
                    .collect(Collectors.toMap(LinhaRubrica::getLinhaPoId, l -> catalogo.get(l.getRubricaId())));
            Integer abertos = x.coluna() ? null : (int) todosAchados.stream()
                    .filter(a -> a.getStatus() == FindingStatus.ABERTO && !a.getReferenceMonth().isBefore(x.inicio())
                            && !a.getReferenceMonth().isAfter(x.fim())).count();
            String rotulo = x.coluna() ? ColunaImpressa.de(x.po(), linhas.findByBudgetIdOrderByPosition(poId))
                    .map(ColunaImpressa::rotulo).orElseThrow()
                    : ServicoExercicios.rotulo(x.inicio(), x.fim());
            entradas.add(new Entrada(x.id(), x.coluna() ? TipoExercicio.COLUNA_IMPRESSA : TipoExercicio.PO, rotulo,
                    poId, x.po().getVersion(), x.inicio(), x.fim(), linhas.findByBudgetIdOrderByPosition(poId),
                    poFundos.findByBudgetId(poId).stream()
                            .collect(Collectors.toMap(BudgetFundLink::getBudgetLineId, BudgetFundLink::getFundId)),
                    daLinha, acumulado, List.copyOf(periodo), List.copyOf(meses), abertos));
        }
        return ComparacaoExercicios.comparar(entradas, new Filtro(fundoId, condominio.getOperatingFundId(),
                mesmosMeses, mesmosMeses ? ComparacaoExercicios.comparando(comuns) : null));
    }

    private Escolhido escolher(UUID condominioId, String id) {
        boolean coluna = id.startsWith("coluna:");
        String texto = id.startsWith("po:") ? id.substring(3) : coluna ? id.substring(7) : id;
        UUID poId;
        try {
            poId = UUID.fromString(texto);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Exercício deve ser po:<id> ou coluna:<id>: " + id);
        }
        Budget po = previsoes.findByIdAndCondominiumId(poId, condominioId)
                .filter(p -> p.getStatus() == BudgetStatus.CONFIRMADA && p.getFiscalYearStart() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Exercício não encontrado (PO confirmada): " + id));
        if (coluna && ColunaImpressa.de(po, linhas.findByBudgetIdOrderByPosition(po.getId())).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "A PO não tem a coluna \"Orçado anterior\": " + id);
        }
        return new Escolhido((coluna ? "coluna:" : "po:") + po.getId(), po, coluna);
    }
}
