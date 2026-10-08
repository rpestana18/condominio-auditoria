package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.auditoria.RegistroAchados;
import br.com.condominioauditoria.backend.auditoria.RegistroAchados.Sincronizacao;
import br.com.condominioauditoria.backend.condominio.CondominioRepository;
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

    private final CondominioRepository condominios;
    private final PrevisaoOrcamentariaRepository previsoes;
    private final ConsultaPrevistoRealizado consulta;
    private final RegistroAchados registro;

    RecalculoAchadosOrcamento(CondominioRepository condominios, PrevisaoOrcamentariaRepository previsoes,
            ConsultaPrevistoRealizado consulta, RegistroAchados registro) {
        this.condominios = condominios;
        this.previsoes = previsoes;
        this.consulta = consulta;
        this.registro = registro;
    }

    /** Resultado por mês recalculado (meses sem números ficam fora). */
    public Map<YearMonth, Sincronizacao> recalcular(MudancaOrcamento mudanca) {
        UUID condominioId = mudanca.condominioId();
        // Um recálculo por vez por condomínio (select ... for update): dois recálculos simultâneos não duplicam
        if (condominios.travar(condominioId).isEmpty()) {
            return Map.of();
        }
        Set<YearMonth> meses = new TreeSet<>();
        previsoes.findByCondominioIdAndEstadoIn(condominioId,
                        EnumSet.of(EstadoPrevisao.CONFIRMADA, EstadoPrevisao.SUBSTITUIDA)).stream()
                .flatMap(p -> java.util.stream.Stream.of(VigenciaPo.de(p), VigenciaPo.prorrogacao(p)))
                .flatMap(java.util.Optional::stream)
                .forEach(v -> meses.addAll(CalculoPrevistoRealizado.meses(v.inicio(), v.fim())));
        Map<YearMonth, Sincronizacao> resultado = new LinkedHashMap<>();
        for (YearMonth mes : meses) {
            AchadosDoMes achados = AchadosDoMes.apurar(mes, consulta.calcular(condominioId, mes.toString(), null));
            if (achados.regras().isEmpty()) {
                continue;
            }
            Sincronizacao s = registro.sincronizar(condominioId, mes, achados.regras(), achados.apurados(),
                    mudanca.gatilho());
            resultado.put(mes, s);
            if (s.abertos() + s.reabertos() + s.encerrados() > 0) {
                log.info("Achados de {} no condomínio {}: {} abertos, {} reabertos, {} não se aplicam mais ({})", mes,
                        condominioId, s.abertos(), s.reabertos(), s.encerrados(), mudanca.gatilho().texto());
            }
        }
        return resultado;
    }
}
