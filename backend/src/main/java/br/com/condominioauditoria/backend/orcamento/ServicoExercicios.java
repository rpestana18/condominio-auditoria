package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.orcamento.CalculoPrevistoRealizado.Fluxo;
import br.com.condominioauditoria.backend.orcamento.ColunaImpressa.DiferencaGrupo;
import br.com.condominioauditoria.backend.orcamento.DeparaDtos.FiltroDepara;
import br.com.condominioauditoria.backend.orcamento.ExercicioDtos.ConferenciaColuna;
import br.com.condominioauditoria.backend.orcamento.ExercicioDtos.Exercicio;
import br.com.condominioauditoria.backend.orcamento.ExercicioDtos.MesDoExercicio;
import br.com.condominioauditoria.backend.orcamento.ExercicioDtos.TipoExercicio;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.Prorrogacao;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.SituacaoMes;
import br.com.condominioauditoria.backend.orcamento.RubricaDtos.FiltroRubrica;
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

    private final PrevisaoOrcamentariaRepository previsoes;
    private final LinhaPoRepository linhas;
    private final ConsultaPrevistoRealizado previstoRealizado;
    private final ServicoDepara depara;
    private final ServicoRubricas rubricas;

    ServicoExercicios(PrevisaoOrcamentariaRepository previsoes, LinhaPoRepository linhas,
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
        List<PrevisaoOrcamentaria> confirmadas = confirmadas(condominioId);
        List<Fluxo> fluxos = previstoRealizado.fluxos(condominioId);
        List<Exercicio> lista = new ArrayList<>();
        for (PrevisaoOrcamentaria po : confirmadas) {
            List<LinhaPo> daPo = linhas.findByPrevisaoIdOrderByOrdem(po.getId());
            // Coluna da PO seguinte que esta PO substituiu (fica só como conferência)
            Optional<PrevisaoOrcamentaria> seguinte = confirmadas.stream()
                    .filter(x -> anterior(confirmadas, x).filter(a -> a.getId().equals(po.getId())).isPresent())
                    .findFirst();
            String colunaSubstituida = null;
            List<String> avisos = List.of();
            if (seguinte.isPresent()) {
                Optional<ColunaImpressa> coluna = ColunaImpressa.de(seguinte.get(),
                        linhas.findByPrevisaoIdOrderByOrdem(seguinte.get().getId()));
                if (coluna.isPresent()) {
                    colunaSubstituida = idColuna(seguinte.get());
                    avisos = coluna.get().diferencas(daPo, tolerancia(seguinte.get())).stream()
                            .map(DiferencaGrupo::texto).toList();
                }
            }
            lista.add(new Exercicio("po:" + po.getId(), TipoExercicio.PO, rotulo(po.getExercicioInicio(),
                    po.getExercicioFim()), po.getId(), po.getVersao(), po.getExercicioInicio().toString(),
                    po.getExercicioFim().toString(), Prorrogacao.de(po), EstruturaPo.de(daPo).previstoMesPelasLinhas()
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
        List<PrevisaoOrcamentaria> confirmadas = confirmadas(condominioId);
        List<String> ids = new ArrayList<>();
        for (PrevisaoOrcamentaria po : confirmadas) {
            ids.add("po:" + po.getId());
            if (anterior(confirmadas, po).isEmpty()
                    && ColunaImpressa.de(po, linhas.findByPrevisaoIdOrderByOrdem(po.getId())).isPresent()) {
                ids.add(idColuna(po));
            }
        }
        return List.copyOf(ids);
    }

    /** Conferência da coluna "Orçado anterior" da PO (RF-11.5). 404 se a PO não está confirmada ou não tem coluna. */
    @Transactional(readOnly = true)
    public ConferenciaColuna colunaImpressa(UUID condominioId, UUID poId) {
        PrevisaoOrcamentaria po = previsoes.findByIdAndCondominioId(poId, condominioId)
                .filter(p -> p.getEstado() == EstadoPrevisao.CONFIRMADA)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO confirmada não encontrada"));
        ColunaImpressa coluna = ColunaImpressa.de(po, linhas.findByPrevisaoIdOrderByOrdem(po.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "A PO não tem a coluna \"Orçado anterior\""));
        Optional<PrevisaoOrcamentaria> anterior = anterior(confirmadas(condominioId), po);
        List<DiferencaGrupo> diferencas = anterior.map(a -> coluna.diferencas(
                linhas.findByPrevisaoIdOrderByOrdem(a.getId()), tolerancia(po))).orElse(List.of());
        List<String> avisos = new ArrayList<>(coluna.avisos());
        diferencas.forEach(d -> avisos.add(d.texto()));
        return new ConferenciaColuna(idColuna(po), coluna.rotulo(), po.getId(), anterior.isPresent(),
                anterior.map(PrevisaoOrcamentaria::getId).orElse(null),
                anterior.map(a -> rotulo(a.getExercicioInicio(), a.getExercicioFim())).orElse(null),
                coluna.totalImpresso(), coluna.totalIncluiFundos(), coluna.fundos(), coluna.previstoMes(),
                coluna.grupos(), diferencas, List.copyOf(avisos));
    }

    /**
     * Exercício anterior da PO: a PO confirmada cujo exercício cobre o mês anterior ao início dela. Os exercícios não
     * se sobrepõem (RF-11.2), então há no máximo uma.
     */
    static Optional<PrevisaoOrcamentaria> anterior(List<PrevisaoOrcamentaria> confirmadas, PrevisaoOrcamentaria po) {
        YearMonth mes = po.getExercicioInicio().minusMonths(1);
        return confirmadas.stream().filter(p -> !p.getId().equals(po.getId()))
                .filter(p -> VigenciaPo.de(p).filter(v -> v.cobre(mes)).isPresent()).findFirst();
    }

    /** "2026/2027" (ou "2026", se o exercício está num ano só). */
    static String rotulo(YearMonth inicio, YearMonth fim) {
        return inicio.getYear() == fim.getYear() ? String.valueOf(inicio.getYear())
                : inicio.getYear() + "/" + fim.getYear();
    }

    private List<PrevisaoOrcamentaria> confirmadas(UUID condominioId) {
        return previsoes.findByCondominioIdAndEstadoIn(condominioId, EnumSet.of(EstadoPrevisao.CONFIRMADA)).stream()
                .filter(p -> p.getExercicioInicio() != null && p.getExercicioFim() != null)
                .sorted(Comparator.comparing(PrevisaoOrcamentaria::getExercicioInicio).reversed()).toList();
    }

    private static Exercicio colunaComoExercicio(PrevisaoOrcamentaria po, ColunaImpressa c) {
        YearMonth inicio = po.getExercicioInicio().minusMonths(12);
        YearMonth fim = po.getExercicioInicio().minusMonths(1);
        return new Exercicio(idColuna(po), TipoExercicio.COLUNA_IMPRESSA, c.rotulo(), po.getId(), po.getVersao(),
                inicio.toString(), fim.toString(), null, c.previstoMes(), List.of(), null, null, null, c.avisos());
    }

    /** Meses do exercício e, depois deles, os prorrogados, com a situação do fluxo de cada um. */
    private static List<MesDoExercicio> meses(PrevisaoOrcamentaria po, List<Fluxo> fluxos) {
        List<MesDoExercicio> meses = new ArrayList<>();
        for (YearMonth m = po.getExercicioInicio(); !m.isAfter(po.getExercicioFim()); m = m.plusMonths(1)) {
            meses.add(new MesDoExercicio(m.toString(), situacao(m, fluxos), false));
        }
        VigenciaPo.prorrogacao(po).ifPresent(v -> {
            for (YearMonth m = v.inicio(); !m.isAfter(v.fim()); m = m.plusMonths(1)) {
                meses.add(new MesDoExercicio(m.toString(), situacao(m, fluxos), true));
            }
        });
        return List.copyOf(meses);
    }

    private static SituacaoMes situacao(YearMonth mes, List<Fluxo> fluxos) {
        long n = fluxos.stream().filter(f -> f.cobre(mes)).count();
        return n == 0 ? SituacaoMes.SEM_FLUXO : n == 1 ? SituacaoMes.COM_FLUXO : SituacaoMes.DOIS_FLUXOS;
    }

    private static BigDecimal tolerancia(PrevisaoOrcamentaria po) {
        return po.getToleranciaArredondamento() == null ? new BigDecimal("0.01") : po.getToleranciaArredondamento();
    }

    static String idColuna(PrevisaoOrcamentaria po) {
        return "coluna:" + po.getId();
    }
}
