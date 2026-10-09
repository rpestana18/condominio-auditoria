package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.model.accounting.Fund;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.audit.RuleParameter;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetFundLink;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.model.enums.BudgetWarningCode;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.FileStatus;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.orcamento.CalculoPrevistoRealizado.Acumulado;
import br.com.condominioauditoria.api.orcamento.CalculoPrevistoRealizado.Calculo;
import br.com.condominioauditoria.api.orcamento.CalculoPrevistoRealizado.Entrada;
import br.com.condominioauditoria.api.orcamento.CalculoPrevistoRealizado.Fluxo;
import br.com.condominioauditoria.api.orcamento.CalculoPrevistoRealizado.Mes;
import br.com.condominioauditoria.api.orcamento.CalculoPrevistoRealizado.Periodo;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.Aviso;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.Evidencia;
import br.com.condominioauditoria.api.repository.accounting.FundRepository;
import br.com.condominioauditoria.api.repository.accounting.LedgerEntryRepository;
import br.com.condominioauditoria.api.repository.audit.RuleParameterRepository;
import br.com.condominioauditoria.api.repository.budget.AccountMappingRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetFundLinkRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.service.audit.rule.MonthlyOverrunRule;
import br.com.condominioauditoria.api.service.budget.BudgetQueryService;
import br.com.condominioauditoria.api.service.calculator.BudgetValidity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Monta a entrada do {@link CalculoPrevistoRealizado} a partir do banco e chama a função. Nada é gravado: os números
 * são calculados a cada consulta (ADR 0004, Decisão 5), a partir dos dados já processados (lançamentos, PO, de-para).
 */
@Service
public class ConsultaPrevistoRealizado {

    /** Arquivos de fluxo lidos (com ou sem conferência falhando), como no painel. */
    static final Set<FileStatus> FLUXO_LIDO = EnumSet.of(FileStatus.CONCLUIDO, FileStatus.PRECISA_REVISAO);
    private static final Set<BudgetWarningCode> AVISOS_DA_PO = EnumSet.of(BudgetWarningCode.ARREDONDAMENTO,
            BudgetWarningCode.CONFIRMADA_COM_DIVERGENCIA);

    private final CondominiumRepository condominios;
    private final BudgetRepository previsoes;
    private final BudgetLineRepository linhas;
    private final AccountMappingRepository deparas;
    private final BudgetFundLinkRepository poFundos;
    private final FundRepository fundos;
    private final SourceFileRepository arquivos;
    private final LedgerEntryRepository lancamentos;
    private final RuleParameterRepository parametros;
    private final BudgetQueryService consultaPrevisao;
    private final RealocacaoLancamentoRepository realocacoes;

    ConsultaPrevistoRealizado(CondominiumRepository condominios, BudgetRepository previsoes,
            BudgetLineRepository linhas, AccountMappingRepository deparas, BudgetFundLinkRepository poFundos,
            FundRepository fundos, SourceFileRepository arquivos, LedgerEntryRepository lancamentos,
            RuleParameterRepository parametros, BudgetQueryService consultaPrevisao,
            RealocacaoLancamentoRepository realocacoes) {
        this.condominios = condominios;
        this.previsoes = previsoes;
        this.linhas = linhas;
        this.deparas = deparas;
        this.poFundos = poFundos;
        this.fundos = fundos;
        this.arquivos = arquivos;
        this.lancamentos = lancamentos;
        this.parametros = parametros;
        this.consultaPrevisao = consultaPrevisao;
        this.realocacoes = realocacoes;
    }

    @Transactional(readOnly = true)
    public PrevistoRealizado consultar(UUID condominioId, String periodo, UUID poId) {
        return calcular(condominioId, periodo, poId).resultado();
    }

    /** Com o filtro de fundo (RF-03.1.13); {@code fundoId} nulo = todos. */
    @Transactional(readOnly = true)
    public PrevistoRealizado consultar(UUID condominioId, String periodo, UUID poId, UUID fundoId) {
        return calcular(condominioId, periodo, poId, fundoId).resultado();
    }

    /** O fundo do filtro, que tem de ser deste condomínio (404 se não for). */
    Fund fundoDoFiltro(UUID condominioId, UUID fundoId) {
        return fundos.findByCondominiumId(condominioId).stream().filter(f -> f.getId().equals(fundoId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fundo não encontrado"));
    }

    /** Cálculo com o filtro de fundo aplicado depois (só esconde; ver {@link VisaoPorFundo}). */
    Calculo calcular(UUID condominioId, String periodo, UUID poId, UUID fundoId) {
        if (fundoId == null) {
            return calcular(condominioId, periodo, poId);
        }
        fundoDoFiltro(condominioId, fundoId);
        UUID ordinario = condominios.findById(condominioId).map(Condominium::getOperatingFundId).orElse(null);
        return VisaoPorFundo.filtrar(calcular(condominioId, periodo, poId), fundoId, ordinario);
    }

    /**
     * Lançamentos que compõem um número: "linha:&lt;id&gt;", "grupo:&lt;id&gt;", "total", "fundo:&lt;id&gt;", AJUSTES,
     * A_REALOCAR, SEM_LINHA_PO, TRANSFERENCIAS.
     */
    @Transactional(readOnly = true)
    public List<Evidencia> evidencia(UUID condominioId, String periodo, UUID poId, String alvo) {
        if (alvo == null || alvo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o alvo da evidência");
        }
        return CalculoPrevistoRealizado.evidencia(calcular(condominioId, periodo, poId), alvo);
    }

    Calculo calcular(UUID condominioId, String periodoTexto, UUID poId) {
        Condominium condominio = condominios.findById(condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        Periodo periodo = periodo(periodoTexto);
        Budget po = escolherPo(condominioId, periodo, poId).orElse(null);
        if (po == null) {
            return CalculoPrevistoRealizado.calcular(new Entrada(null, null, null, null, null, null,
                    condominio.getOperatingFundId(), null, null, null, null, null, periodo));
        }
        List<BudgetLine> lidas = linhas.findByBudgetIdOrderByPosition(po.getId());
        Map<UUID, UUID> fundoPorLinha = poFundos.findByBudgetId(po.getId()).stream()
                .collect(Collectors.toMap(BudgetFundLink::getBudgetLineId, BudgetFundLink::getFundId));
        Map<UUID, String> nomes = fundos.findByCondominiumId(condominioId).stream()
                .collect(Collectors.toMap(Fund::getId, Fund::getName));
        List<Fluxo> fluxos = fluxos(condominioId);

        LocalDate inicio;
        LocalDate fim;
        var vigencia = BudgetValidity.of(po);
        if (periodo instanceof Mes m) {
            inicio = m.mes().atDay(1);
            fim = m.mes().atEndOfMonth();
        } else if (vigencia.isPresent()) {
            // Acumulado: o exercício e, depois dele, os meses prorrogados (mostrados fora da soma, RF-11.3)
            inicio = vigencia.get().start().atDay(1);
            fim = BudgetValidity.extension(po).map(BudgetValidity::end).orElse(vigencia.get().end()).atEndOfMonth();
        } else {
            inicio = LocalDate.MIN;
            fim = LocalDate.MIN;
        }
        List<LedgerEntry> doPeriodo = fluxos.isEmpty() || inicio.equals(LocalDate.MIN) ? List.of()
                : lancamentos.findByFileIdInAndDateBetween(fluxos.stream().map(Fluxo::arquivoId).toList(), inicio,
                        fim);
        BigDecimal limite = po.getFiscalYearStart() == null ? null
                : parametros.findValidOn(condominioId, MonthlyOverrunRule.PARAMETER, inicio.equals(LocalDate.MIN)
                        ? po.getFiscalYearStart().atDay(1) : inicio).map(RuleParameter::getValue).orElse(null);
        List<Aviso> avisosDaPo = consultaPrevisao.detail(po).warnings().stream()
                .filter(a -> AVISOS_DA_PO.contains(a.code())).map(a -> new Aviso(a.code().name(), a.text()))
                .toList();
        String nomeArquivo = arquivos.findById(po.getFileId()).map(SourceFile::getOriginalName).orElse(null);
        // Realocações ativas desta versão da PO (RF-03.1.7), religadas aos lançamentos pela chave estável
        List<CalculoPrevistoRealizado.Realocacao> ativas = realocacoes.findByPrevisaoIdAndDesfeitaEmIsNull(po.getId())
                .stream().map(RealocacaoLancamento::paraCalculo).toList();
        return CalculoPrevistoRealizado.calcular(new Entrada(po, nomeArquivo, lidas,
                deparas.findByBudgetIdOrderByAccountCode(po.getId()), fundoPorLinha, nomes,
                condominio.getOperatingFundId(), fluxos, doPeriodo, ativas, limite, avisosDaPo, periodo));
    }

    /** Fluxos lidos do condomínio com período (balancetes concluídos ou a revisar). */
    List<Fluxo> fluxos(UUID condominioId) {
        return arquivos.findByCondominiumIdAndCategoryAndStatusIn(condominioId, FileCategory.BALANCETE, FLUXO_LIDO)
                .stream().filter(a -> a.getPeriodStart() != null && a.getPeriodEnd() != null)
                .map(a -> new Fluxo(a.getId(), a.getOriginalName(), a.getSha256(), a.getPeriodStart(),
                        a.getPeriodEnd(), a.getUploadedAt(), a.getUploadedBy()))
                .sorted(Comparator.comparing(Fluxo::arquivoId)).toList();
    }

    private Optional<Budget> escolherPo(UUID condominioId, Periodo periodo, UUID poId) {
        if (poId != null) {
            return Optional.of(previsoes.findByIdAndCondominiumId(poId, condominioId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO não encontrada")));
        }
        if (periodo instanceof Mes m) {
            return consultaPrevisao.activeInMonth(condominioId, m.mes());
        }
        // Acumulado sem PO informada: a versão confirmada mais recente
        return previsoes.findByCondominiumIdAndStatusIn(condominioId, EnumSet.of(BudgetStatus.CONFIRMADA)).stream()
                .filter(p -> p.getVersion() != null).max(Comparator.comparing(Budget::getVersion));
    }

    static Periodo periodo(String texto) {
        String t = texto == null ? "" : texto.trim();
        if (t.equalsIgnoreCase("acumulado")) {
            return new Acumulado();
        }
        try {
            return new Mes(YearMonth.parse(t));
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Período deve ser AAAA-MM (ex.: 2026-09) ou \"acumulado\": " + texto);
        }
    }
}
