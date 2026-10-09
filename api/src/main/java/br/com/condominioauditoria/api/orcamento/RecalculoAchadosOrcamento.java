package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.event.BudgetChanged;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.service.audit.FindingSyncService;
import br.com.condominioauditoria.api.service.audit.FindingSyncService.SyncResult;
import br.com.condominioauditoria.api.service.calculator.BudgetValidity;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Recalcula os achados do orçamento de um condomínio depois de cada mudança (ADR 0004, Decisão 5; RF-03.1.12): para
 * cada mês de uma PO confirmada (exercício e meses prorrogados, RF-11.3), usa o mesmo cálculo da tela e grava pela
 * chave única. Mês sem números (sem fluxo, dois fluxos) não muda nenhum achado. Chamado por
 * {@link DisparoRecalculoAchados} depois do commit; quem chama abre a transação.
 */
@Service
public class RecalculoAchadosOrcamento {

    private static final Logger log = LoggerFactory.getLogger(RecalculoAchadosOrcamento.class);

    private final CondominiumRepository condominios;
    private final BudgetRepository previsoes;
    private final ConsultaPrevistoRealizado consulta;
    private final FindingSyncService registro;

    RecalculoAchadosOrcamento(CondominiumRepository condominios, BudgetRepository previsoes,
            ConsultaPrevistoRealizado consulta, FindingSyncService registro) {
        this.condominios = condominios;
        this.previsoes = previsoes;
        this.consulta = consulta;
        this.registro = registro;
    }

    /** Resultado por mês recalculado (meses sem números ficam fora). */
    public Map<YearMonth, SyncResult> recalcular(BudgetChanged mudanca) {
        UUID condominioId = mudanca.condominiumId();
        // Um recálculo por vez por condomínio (select ... for update): dois recálculos simultâneos não duplicam
        if (condominios.lockById(condominioId).isEmpty()) {
            return Map.of();
        }
        Set<YearMonth> meses = new TreeSet<>();
        previsoes.findByCondominiumIdAndStatusIn(condominioId,
                        EnumSet.of(BudgetStatus.CONFIRMADA, BudgetStatus.SUBSTITUIDA)).stream()
                .flatMap(p -> java.util.stream.Stream.of(BudgetValidity.of(p), BudgetValidity.extension(p)))
                .flatMap(java.util.Optional::stream)
                .forEach(v -> meses.addAll(CalculoPrevistoRealizado.meses(v.start(), v.end())));
        Map<YearMonth, SyncResult> resultado = new LinkedHashMap<>();
        for (YearMonth mes : meses) {
            AchadosDoMes achados = AchadosDoMes.apurar(mes, consulta.calcular(condominioId, mes.toString(), null));
            if (achados.regras().isEmpty()) {
                continue;
            }
            SyncResult s = registro.synchronize(condominioId, mes, achados.regras(), achados.apurados(),
                    mudanca.trigger());
            resultado.put(mes, s);
            if (s.opened() + s.reopened() + s.closed() > 0) {
                log.info("Achados de {} no condomínio {}: {} abertos, {} reabertos, {} não se aplicam mais ({})", mes,
                        condominioId, s.opened(), s.reopened(), s.closed(), mudanca.trigger().text());
            }
        }
        return resultado;
    }
}
