package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.orcamento.CalculoPrevistoRealizado.Fluxo;
import br.com.condominioauditoria.api.orcamento.Indicadores.MesCalculado;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.Situacao;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.SituacaoMes;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Monta a entrada dos {@link Indicadores} a partir do banco (RF-11.10 a RF-11.12): o acumulado do exercício e um
 * cálculo por mês com fluxo, pela mesma consulta da tela de previsto × realizado, e a comparação com o exercício
 * anterior (RF-11.6). Nada é gravado.
 */
@Service
public class ServicoIndicadores {

    private final CondominiumRepository condominios;
    private final BudgetRepository previsoes;
    private final ConsultaPrevistoRealizado previstoRealizado;
    private final ServicoExercicios exercicios;
    private final ServicoComparacao comparacao;

    ServicoIndicadores(CondominiumRepository condominios, BudgetRepository previsoes,
            ConsultaPrevistoRealizado previstoRealizado, ServicoExercicios exercicios, ServicoComparacao comparacao) {
        this.condominios = condominios;
        this.previsoes = previsoes;
        this.previstoRealizado = previstoRealizado;
        this.exercicios = exercicios;
        this.comparacao = comparacao;
    }

    /** {@code poId} nulo = o exercício mais recente; {@code fundoId} nulo = todos. */
    @Transactional(readOnly = true)
    public Indicadores.Resultado indicadores(UUID condominioId, UUID poId, UUID fundoId) {
        Condominium condominio = condominios.findById(condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        if (fundoId != null) {
            previstoRealizado.fundoDoFiltro(condominioId, fundoId);
        }
        List<String> ids = exercicios.ids(condominioId);
        UUID escolhida = poId != null ? poId : ids.stream().filter(id -> id.startsWith("po:")).findFirst()
                .map(id -> UUID.fromString(id.substring(3)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nenhuma PO confirmada"));
        Budget po = previsoes.findByIdAndCondominiumId(escolhida, condominioId)
                .filter(p -> p.getStatus() == BudgetStatus.CONFIRMADA && p.getFiscalYearStart() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO confirmada não encontrada"));

        PrevistoRealizado acumulado = previstoRealizado.calcular(condominioId, "acumulado", po.getId()).resultado();
        List<Fluxo> fluxos = previstoRealizado.fluxos(condominioId);
        List<MesCalculado> meses = new ArrayList<>();
        for (YearMonth m = po.getFiscalYearStart(); !m.isAfter(po.getFiscalYearEnd()); m = m.plusMonths(1)) {
            SituacaoMes situacao = ServicoExercicios.situacao(m, fluxos);
            PrevistoRealizado doMes = null;
            if (situacao == SituacaoMes.COM_FLUXO) {
                PrevistoRealizado r = previstoRealizado.calcular(condominioId, m.toString(), po.getId()).resultado();
                doMes = r.situacao() == Situacao.CALCULADO ? r : null;
            }
            meses.add(new MesCalculado(m, situacao, doMes));
        }

        // Gráfico 7: este exercício e o anterior da lista (PO ou coluna impressa)
        int i = ids.indexOf("po:" + po.getId());
        ComparacaoExercicios.Resultado comparado = null;
        String semComparacao = null;
        if (i >= 0 && i + 1 < ids.size()) {
            comparado = comparacao.comparar(condominioId, List.of(ids.get(i), ids.get(i + 1)), fundoId, false);
        } else {
            semComparacao = "Comparação entre exercícios: sem exercício anterior a este";
        }
        return Indicadores.montar(new Indicadores.Entrada(po, ServicoExercicios.rotulo(po.getFiscalYearStart(),
                po.getFiscalYearEnd()), acumulado, List.copyOf(meses), fundoId, condominio.getOperatingFundId(),
                comparado, semComparacao));
    }
}
