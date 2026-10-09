package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.auditoria.Achado;
import br.com.condominioauditoria.api.auditoria.AchadoRepository;
import br.com.condominioauditoria.api.auditoria.EstadoAchado;
import br.com.condominioauditoria.api.condominio.Condominio;
import br.com.condominioauditoria.api.condominio.CondominioRepository;
import br.com.condominioauditoria.api.orcamento.ComparacaoExercicios.Entrada;
import br.com.condominioauditoria.api.orcamento.ComparacaoExercicios.Filtro;
import br.com.condominioauditoria.api.orcamento.ComparacaoExercicios.Resultado;
import br.com.condominioauditoria.api.orcamento.ComparacaoExercicios.RubricaDaLinha;
import br.com.condominioauditoria.api.orcamento.ExercicioDtos.TipoExercicio;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.Situacao;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.SituacaoMes;
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

    private final CondominioRepository condominios;
    private final PrevisaoOrcamentariaRepository previsoes;
    private final LinhaPoRepository linhas;
    private final PoFundoRepository poFundos;
    private final RubricaRepository rubricas;
    private final LinhaRubricaRepository linhasRubrica;
    private final AchadoRepository achados;
    private final ConsultaPrevistoRealizado previstoRealizado;
    private final ServicoExercicios exercicios;

    ServicoComparacao(CondominioRepository condominios, PrevisaoOrcamentariaRepository previsoes,
            LinhaPoRepository linhas, PoFundoRepository poFundos, RubricaRepository rubricas,
            LinhaRubricaRepository linhasRubrica, AchadoRepository achados,
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
    private record Escolhido(String id, PrevisaoOrcamentaria po, boolean coluna) {

        YearMonth inicio() {
            return coluna ? po.getExercicioInicio().minusMonths(12) : po.getExercicioInicio();
        }

        YearMonth fim() {
            return coluna ? po.getExercicioInicio().minusMonths(1) : po.getExercicioFim();
        }
    }

    /**
     * {@code ids}: "po:&lt;uuid&gt;" (ou só o uuid) e "coluna:&lt;uuid&gt;"; vazio = os dois exercícios mais recentes.
     * {@code fundoId} nulo = todos.
     */
    @Transactional(readOnly = true)
    public Resultado comparar(UUID condominioId, List<String> ids, UUID fundoId, boolean mesmosMeses) {
        Condominio condominio = condominios.findById(condominioId)
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
        List<Achado> todosAchados = achados.findByCondominioIdOrderByCompetenciaDescCriadoEmAsc(condominioId);
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
                    .filter(a -> a.getEstado() == EstadoAchado.ABERTO && !a.getCompetencia().isBefore(x.inicio())
                            && !a.getCompetencia().isAfter(x.fim())).count();
            String rotulo = x.coluna() ? ColunaImpressa.de(x.po(), linhas.findByPrevisaoIdOrderByOrdem(poId))
                    .map(ColunaImpressa::rotulo).orElseThrow()
                    : ServicoExercicios.rotulo(x.inicio(), x.fim());
            entradas.add(new Entrada(x.id(), x.coluna() ? TipoExercicio.COLUNA_IMPRESSA : TipoExercicio.PO, rotulo,
                    poId, x.po().getVersao(), x.inicio(), x.fim(), linhas.findByPrevisaoIdOrderByOrdem(poId),
                    poFundos.findByPrevisaoId(poId).stream()
                            .collect(Collectors.toMap(PoFundo::getLinhaPoId, PoFundo::getFundoId)),
                    daLinha, acumulado, List.copyOf(periodo), List.copyOf(meses), abertos));
        }
        return ComparacaoExercicios.comparar(entradas, new Filtro(fundoId, condominio.getFundoOrdinarioId(),
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
        PrevisaoOrcamentaria po = previsoes.findByIdAndCondominioId(poId, condominioId)
                .filter(p -> p.getEstado() == EstadoPrevisao.CONFIRMADA && p.getExercicioInicio() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Exercício não encontrado (PO confirmada): " + id));
        if (coluna && ColunaImpressa.de(po, linhas.findByPrevisaoIdOrderByOrdem(po.getId())).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "A PO não tem a coluna \"Orçado anterior\": " + id);
        }
        return new Escolhido((coluna ? "coluna:" : "po:") + po.getId(), po, coluna);
    }
}
