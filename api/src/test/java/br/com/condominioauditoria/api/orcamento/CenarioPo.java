package br.com.condominioauditoria.api.orcamento;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.config.properties.AccountMappingProperties;
import br.com.condominioauditoria.api.config.properties.BudgetProperties;
import br.com.condominioauditoria.api.dto.request.budget.BudgetConfirmationRequest;
import br.com.condominioauditoria.api.dto.request.budget.EffectiveCodeRequest;
import br.com.condominioauditoria.api.dto.request.budget.FundLinkRequest;
import br.com.condominioauditoria.api.event.BudgetChanged;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetData;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.Enrichment;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.LedgerEntryData;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.TotalsCheckData;
import br.com.condominioauditoria.api.model.accounting.Fund;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.accounting.TotalsCheck;
import br.com.condominioauditoria.api.model.audit.Finding;
import br.com.condominioauditoria.api.model.audit.FindingEvent;
import br.com.condominioauditoria.api.model.audit.FindingEvidence;
import br.com.condominioauditoria.api.model.audit.RuleParameter;
import br.com.condominioauditoria.api.model.budget.AccountMapping;
import br.com.condominioauditoria.api.model.budget.AccountMappingEvent;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetEvent;
import br.com.condominioauditoria.api.model.budget.BudgetFundLink;
import br.com.condominioauditoria.api.model.budget.BudgetItem;
import br.com.condominioauditoria.api.model.budget.BudgetItemEvent;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.BudgetLineItem;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.FileStatus;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.accounting.FundRepository;
import br.com.condominioauditoria.api.repository.accounting.LedgerEntryRepository;
import br.com.condominioauditoria.api.repository.accounting.TotalsCheckRepository;
import br.com.condominioauditoria.api.repository.audit.FindingEventRepository;
import br.com.condominioauditoria.api.repository.audit.FindingEvidenceRepository;
import br.com.condominioauditoria.api.repository.audit.FindingRepository;
import br.com.condominioauditoria.api.repository.audit.RuleParameterRepository;
import br.com.condominioauditoria.api.repository.budget.AccountMappingEventRepository;
import br.com.condominioauditoria.api.repository.budget.AccountMappingRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetEventRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetFundLinkRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetItemEventRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetItemRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineItemRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.service.audit.FindingSyncService;
import br.com.condominioauditoria.api.service.audit.rule.MonthlyOverrunRule;
import br.com.condominioauditoria.api.service.audit.rule.ReserveFundCapRule;
import br.com.condominioauditoria.api.service.budget.AccountMappingService;
import br.com.condominioauditoria.api.service.budget.BudgetConfirmationService;
import br.com.condominioauditoria.api.service.budget.BudgetExtensionService;
import br.com.condominioauditoria.api.service.budget.BudgetFundLinkService;
import br.com.condominioauditoria.api.service.budget.BudgetImportService;
import br.com.condominioauditoria.api.service.budget.BudgetItemService;
import br.com.condominioauditoria.api.service.budget.BudgetQueryService;
import br.com.condominioauditoria.api.service.budget.BudgetReserveFundService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Repositórios em memória para testar a confirmação da PO sem banco. */
public final class CenarioPo {

    public final UUID condominioId = UUID.randomUUID();
    public final Condominium condominio = mock(Condominium.class);
    public final Fund ordinario = new Fund(condominioId, "CONDOMÍNIO");
    public final Fund reserva = new Fund(condominioId, "FUNDO DE RESERVA");
    public final Fund obrasInfra = new Fund(condominioId, "OBRAS / REFORMAS / INFRA");
    public final Fund obras = new Fund(condominioId, "OBRAS");
    public final SourceFile ata;

    public final Map<UUID, SourceFile> arquivos = new LinkedHashMap<>();
    public final Map<UUID, Budget> previsoes = new LinkedHashMap<>();
    public final List<BudgetLine> linhas = new ArrayList<>();
    public final List<TotalsCheck> conferencias = new ArrayList<>();
    public final List<BudgetFundLink> poFundos = new ArrayList<>();
    public final List<BudgetEvent> eventos = new ArrayList<>();
    public final List<Finding> achados = new ArrayList<>();
    public final List<FindingEvidence> evidencias = new ArrayList<>();
    public final List<RuleParameter> parametros = new ArrayList<>();
    public final List<AccountMapping> deparas = new ArrayList<>();
    public final List<AccountMappingEvent> eventosDepara = new ArrayList<>();
    public final List<LedgerEntry> lancamentos = new ArrayList<>();
    public final List<FindingEvent> eventosAchado = new ArrayList<>();
    public final List<RealocacaoLancamento> realocacoes = new ArrayList<>();
    public final List<EventoRealocacao> eventosRealocacao = new ArrayList<>();
    public final List<Fund> fundos = new ArrayList<>();
    public final List<BudgetItem> rubricas = new ArrayList<>();
    public final List<BudgetLineItem> linhasRubrica = new ArrayList<>();
    public final List<BudgetItemEvent> eventosRubrica = new ArrayList<>();
    /** Mudanças publicadas pelos serviços; {@link #aposCommit()} faz o papel do disparo depois do commit. */
    public final List<Object> publicados = new ArrayList<>();

    public final BudgetConfirmationService confirmacao;
    public final BudgetQueryService consulta;
    public final AccountMappingService depara;
    public final BudgetItemService servicoRubricas;
    public final BudgetExtensionService prorrogacao;
    public final ServicoExercicios exercicios;
    public final ServicoComparacao comparacao;
    public final ServicoIndicadores indicadores;
    public final BudgetRepository previsaoRepo;
    public final BudgetLineRepository linhaRepo;
    public final AccountMappingRepository deparaRepo;
    public final AccountMappingEventRepository eventoDeparaRepo;
    public final ConsultaPrevistoRealizado previstoRealizado;
    public final ServicoRealocacao realocacao;
    public final RecalculoAchadosOrcamento recalculo;
    public final FindingSyncService registro;
    public final BudgetFundLinkService ligacaoFundos;
    private final BudgetImportService gravacao;

    public CenarioPo() {
        when(condominio.getId()).thenReturn(condominioId);
        when(condominio.getOperatingFundId()).thenReturn(ordinario.getId());
        CondominiumRepository condominios = mock(CondominiumRepository.class);
        when(condominios.lockById(condominioId)).thenReturn(Optional.of(condominio));
        when(condominios.findById(condominioId)).thenReturn(Optional.of(condominio));

        SourceFileRepository arquivoRepo = mock(SourceFileRepository.class);
        when(arquivoRepo.findById(any())).thenAnswer(i -> Optional.ofNullable(arquivos.get(i.<UUID>getArgument(0))));
        when(arquivoRepo.findByIdAndCondominiumId(any(), any())).thenAnswer(i -> Optional
                .ofNullable(arquivos.get(i.<UUID>getArgument(0))).filter(a -> a.getCondominiumId().equals(i.getArgument(1))));

        previsaoRepo = mock(BudgetRepository.class);
        when(previsaoRepo.save(any())).thenAnswer(i -> {
            Budget p = i.getArgument(0);
            previsoes.put(p.getId(), p);
            return p;
        });
        when(previsaoRepo.findByFileId(any())).thenAnswer(i -> previsoes.values().stream()
                .filter(p -> p.getFileId().equals(i.getArgument(0))).findFirst());
        when(previsaoRepo.findByIdAndCondominiumId(any(), any())).thenAnswer(i -> Optional
                .ofNullable(previsoes.get(i.<UUID>getArgument(0))).filter(p -> p.getCondominiumId().equals(i.getArgument(1))));
        when(previsaoRepo.findByCondominiumIdAndStatusIn(any(), any())).thenAnswer(i -> previsoes.values().stream()
                .filter(p -> p.getCondominiumId().equals(i.getArgument(0))
                        && i.<Collection<BudgetStatus>>getArgument(1).contains(p.getStatus()))
                .toList());

        linhaRepo = mock(BudgetLineRepository.class);
        when(linhaRepo.saveAll(any())).thenAnswer(i -> {
            for (BudgetLine l : i.<Iterable<BudgetLine>>getArgument(0)) {
                if (!linhas.contains(l)) {
                    linhas.add(l);
                }
            }
            return i.getArgument(0);
        });
        when(linhaRepo.findByBudgetIdOrderByPosition(any())).thenAnswer(i -> linhas.stream()
                .filter(l -> l.getBudgetId().equals(i.getArgument(0))).sorted(Comparator.comparingInt(BudgetLine::getPosition))
                .toList());

        TotalsCheckRepository conferenciaRepo = mock(TotalsCheckRepository.class);
        when(conferenciaRepo.findByFileIdOrderBySequence(any())).thenAnswer(i -> conferencias.stream()
                .filter(c -> c.getFileId().equals(i.getArgument(0))).toList());

        FundRepository fundoRepo = mock(FundRepository.class);
        fundos.addAll(List.of(ordinario, reserva, obrasInfra, obras));
        when(fundoRepo.findByCondominiumId(condominioId)).thenAnswer(i -> List.copyOf(fundos));
        when(arquivoRepo.findByCondominiumIdAndCategoryAndStatusIn(any(), any(), any())).thenAnswer(i -> arquivos
                .values().stream().filter(a -> a.getCondominiumId().equals(i.getArgument(0))
                        && a.getCategory() == i.getArgument(1)
                        && i.<Collection<FileStatus>>getArgument(2).contains(a.getStatus()))
                .toList());

        BudgetFundLinkRepository poFundoRepo = mock(BudgetFundLinkRepository.class);
        when(poFundoRepo.save(any())).thenAnswer(i -> {
            poFundos.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(poFundoRepo.findByBudgetId(any())).thenAnswer(i -> poFundos.stream()
                .filter(f -> f.getBudgetId().equals(i.getArgument(0))).toList());
        org.mockito.Mockito.doAnswer(i -> poFundos.removeIf(f -> f.getBudgetId().equals(i.getArgument(0))))
                .when(poFundoRepo).deleteByBudgetId(any());

        BudgetEventRepository eventoRepo = mock(BudgetEventRepository.class);
        when(eventoRepo.save(any())).thenAnswer(i -> {
            eventos.add(i.getArgument(0));
            return i.getArgument(0);
        });

        FindingRepository achadoRepo = mock(FindingRepository.class);
        when(achadoRepo.save(any())).thenAnswer(i -> {
            if (!achados.contains(i.<Finding>getArgument(0))) {
                achados.add(i.getArgument(0));
            }
            return i.getArgument(0);
        });
        when(achadoRepo.findByCondominiumIdAndReferenceMonthAndRuleIn(any(), any(), any())).thenAnswer(i -> achados
                .stream().filter(a -> a.getCondominiumId().equals(i.getArgument(0))
                        && a.getReferenceMonth().atDay(1).equals(i.getArgument(1))
                        && i.<Collection<String>>getArgument(2).contains(a.getRule()))
                .toList());
        FindingEventRepository eventoAchadoRepo = mock(FindingEventRepository.class);
        when(eventoAchadoRepo.save(any())).thenAnswer(i -> {
            eventosAchado.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(achadoRepo.findByCondominiumIdAndRuleAndReferenceMonthAndTarget(any(), anyString(), any(), anyString()))
                .thenAnswer(i -> achados.stream().filter(a -> a.getCondominiumId().equals(i.getArgument(0))
                        && a.getRule().equals(i.getArgument(1))
                        && a.getReferenceMonth().atDay(1).equals(i.getArgument(2)) && a.getTarget().equals(i.getArgument(3)))
                        .findFirst());
        when(achadoRepo.findByCondominiumIdAndTargetStartingWithOrderByCreatedAt(any(),
                anyString())).thenAnswer(i -> achados
                .stream().filter(a -> a.getCondominiumId().equals(i.getArgument(0))
                        && a.getTarget().startsWith(i.getArgument(1))).toList());
        when(achadoRepo.findByCondominiumIdOrderByReferenceMonthDescCreatedAtAsc(any())).thenAnswer(i -> achados.stream()
                .filter(a -> a.getCondominiumId().equals(i.getArgument(0))).toList());
        FindingEvidenceRepository evidenciaRepo = mock(FindingEvidenceRepository.class);
        when(evidenciaRepo.save(any())).thenAnswer(i -> {
            evidencias.add(i.getArgument(0));
            return i.getArgument(0);
        });

        RuleParameterRepository parametroRepo = mock(RuleParameterRepository.class);
        when(parametroRepo.findValidOn(any(), anyString(), any())).thenAnswer(i -> parametros.stream()
                .filter(p -> p.getCode().equals(i.getArgument(1))
                        && !p.getValidFrom().isAfter(i.getArgument(2)))
                .findFirst());
        parametros.add(new RuleParameter(condominioId, ReserveFundCapRule.PARAMETER, new BigDecimal("5.0000"),
                LocalDate.of(1900, 1, 1), null, "Conv. 20.1"));
        parametros.add(new RuleParameter(condominioId, MonthlyOverrunRule.PARAMETER, new BigDecimal("20.0000"),
                LocalDate.of(1900, 1, 1), null, "Conv. 16.2"));

        BudgetReserveFundService reservaDaPo = new BudgetReserveFundService(parametroRepo);
        consulta = new BudgetQueryService(previsaoRepo, linhaRepo, conferenciaRepo, arquivoRepo, poFundoRepo, fundoRepo,
                achadoRepo, reservaDaPo, eventoRepo);
        registro = new FindingSyncService(achadoRepo, evidenciaRepo, eventoAchadoRepo);
        BudgetItemRepository rubricaRepo = mock(BudgetItemRepository.class);
        when(rubricaRepo.save(any())).thenAnswer(i -> {
            if (!rubricas.contains(i.<BudgetItem>getArgument(0))) {
                rubricas.add(i.getArgument(0));
            }
            return i.getArgument(0);
        });
        when(rubricaRepo.existsByCondominiumId(any())).thenAnswer(i -> rubricas.stream()
                .anyMatch(r -> r.getCondominiumId().equals(i.getArgument(0))));
        when(rubricaRepo.findByCondominiumIdOrderByName(any())).thenAnswer(i -> rubricas.stream()
                .filter(r -> r.getCondominiumId().equals(i.getArgument(0))).sorted(Comparator.comparing(BudgetItem::getName))
                .toList());
        when(rubricaRepo.findById(any())).thenAnswer(i -> rubricas.stream()
                .filter(r -> r.getId().equals(i.getArgument(0))).findFirst());
        when(rubricaRepo.findByIdAndCondominiumId(any(), any())).thenAnswer(i -> rubricas.stream()
                .filter(r -> r.getId().equals(i.getArgument(0)) && r.getCondominiumId().equals(i.getArgument(1)))
                .findFirst());
        BudgetLineItemRepository linhaRubricaRepo = mock(BudgetLineItemRepository.class);
        when(linhaRubricaRepo.save(any())).thenAnswer(i -> {
            if (!linhasRubrica.contains(i.<BudgetLineItem>getArgument(0))) {
                linhasRubrica.add(i.getArgument(0));
            }
            return i.getArgument(0);
        });
        when(linhaRubricaRepo.findByBudgetId(any())).thenAnswer(i -> linhasRubrica.stream()
                .filter(l -> l.getBudgetId().equals(i.getArgument(0))).toList());
        when(linhaRubricaRepo.findByCondominiumIdAndStatus(any(), any())).thenAnswer(i -> linhasRubrica.stream()
                .filter(l -> l.getCondominiumId().equals(i.getArgument(0)) && l.getStatus() == i.getArgument(1))
                .toList());
        BudgetItemEventRepository eventoRubricaRepo = mock(BudgetItemEventRepository.class);
        when(eventoRubricaRepo.save(any())).thenAnswer(i -> {
            eventosRubrica.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(eventoRubricaRepo.findByBudgetIdOrderByOccurredAtAscLineCodeAsc(any())).thenAnswer(i -> eventosRubrica.stream()
                .filter(e -> i.getArgument(0).equals(e.getBudgetId())).toList());
        servicoRubricas = new BudgetItemService(condominios, previsaoRepo, linhaRepo, rubricaRepo, linhaRubricaRepo,
                eventoRubricaRepo);
        confirmacao = new BudgetConfirmationService(condominios, previsaoRepo, linhaRepo, poFundoRepo, eventoRepo,
                arquivoRepo, fundoRepo, consulta, reservaDaPo, registro, servicoRubricas, publicados::add);
        gravacao = new BudgetImportService(previsaoRepo, linhaRepo, new BudgetProperties(new BigDecimal("0.01")));

        deparaRepo = mock(AccountMappingRepository.class);
        when(deparaRepo.save(any())).thenAnswer(i -> {
            AccountMapping d = i.getArgument(0);
            if (!deparas.contains(d)) {
                deparas.add(d);
            }
            return d;
        });
        when(deparaRepo.findByBudgetIdOrderByAccountCode(any())).thenAnswer(i -> deparas.stream()
                .filter(d -> d.getBudgetId().equals(i.getArgument(0)))
                .sorted(Comparator.comparing(AccountMapping::getAccountCode)).toList());
        when(deparaRepo.findByBudgetIdAndAccountCode(any(), anyString())).thenAnswer(i -> deparas.stream()
                .filter(d -> d.getBudgetId().equals(i.getArgument(0)) && d.getAccountCode().equals(i.getArgument(1)))
                .findFirst());
        eventoDeparaRepo = mock(AccountMappingEventRepository.class);
        when(eventoDeparaRepo.save(any())).thenAnswer(i -> {
            eventosDepara.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(eventoDeparaRepo.findByBudgetIdOrderByOccurredAtAscAccountCodeAsc(any())).thenAnswer(i -> eventosDepara.stream()
                .filter(e -> e.getBudgetId().equals(i.getArgument(0))).toList());
        LedgerEntryRepository lancamentoRepo = mock(LedgerEntryRepository.class);
        when(lancamentoRepo.debitsWithAccount(any(), any(), any(), any())).thenAnswer(i -> lancamentos.stream()
                .filter(l -> l.getFundId().equals(i.getArgument(1)) && !l.getDate().isBefore(i.getArgument(2))
                        && !l.getDate().isAfter(i.getArgument(3)) && l.getDebit().signum() != 0
                        && !l.isInterFundTransfer() && l.getAccountCode() != null)
                .sorted(Comparator.comparing(LedgerEntry::getDate)).toList());
        when(lancamentoRepo.findByFileIdInAndDateBetween(any(), any(), any())).thenAnswer(i -> lancamentos.stream()
                .filter(l -> i.<Collection<UUID>>getArgument(0).contains(l.getFileId())
                        && !l.getDate().isBefore(i.getArgument(1)) && !l.getDate().isAfter(i.getArgument(2)))
                .toList());
        when(lancamentoRepo.findById(any())).thenAnswer(i -> lancamentos.stream()
                .filter(l -> l.getId().equals(i.getArgument(0))).findFirst());
        depara = new AccountMappingService(condominios, previsaoRepo, linhaRepo, deparaRepo, eventoDeparaRepo,
                lancamentoRepo,
                AccountMappingProperties.defaults(), publicados::add);

        RealocacaoLancamentoRepository realocacaoRepo = mock(RealocacaoLancamentoRepository.class);
        when(realocacaoRepo.save(any())).thenAnswer(i -> {
            if (!realocacoes.contains(i.<RealocacaoLancamento>getArgument(0))) {
                realocacoes.add(i.getArgument(0));
            }
            return i.getArgument(0);
        });
        when(realocacaoRepo.findByPrevisaoIdAndDesfeitaEmIsNull(any())).thenAnswer(i -> realocacoes.stream()
                .filter(r -> r.getPrevisaoId().equals(i.getArgument(0)) && r.ativa()).toList());
        when(realocacaoRepo.findByPrevisaoIdOrderByDataAscRealocadaEmAsc(any())).thenAnswer(i -> realocacoes.stream()
                .filter(r -> r.getPrevisaoId().equals(i.getArgument(0))).toList());
        when(realocacaoRepo.findByPrevisaoIdAndChaveLancamentoAndDesfeitaEmIsNull(any(), anyString()))
                .thenAnswer(i -> realocacoes.stream().filter(r -> r.getPrevisaoId().equals(i.getArgument(0))
                        && r.getChaveLancamento().equals(i.getArgument(1)) && r.ativa()).findFirst());
        when(realocacaoRepo.findByIdAndCondominioId(any(), any())).thenAnswer(i -> realocacoes.stream()
                .filter(r -> r.getId().equals(i.getArgument(0)) && r.getCondominioId().equals(i.getArgument(1)))
                .findFirst());
        EventoRealocacaoRepository eventoRealocacaoRepo = mock(EventoRealocacaoRepository.class);
        when(eventoRealocacaoRepo.save(any())).thenAnswer(i -> {
            eventosRealocacao.add(i.getArgument(0));
            return i.getArgument(0);
        });
        previstoRealizado = new ConsultaPrevistoRealizado(condominios, previsaoRepo, linhaRepo, deparaRepo,
                poFundoRepo, fundoRepo, arquivoRepo, lancamentoRepo, parametroRepo, consulta, realocacaoRepo);
        realocacao = new ServicoRealocacao(condominios, lancamentoRepo, arquivoRepo, consulta, previsaoRepo,
                linhaRepo, deparaRepo, realocacaoRepo, eventoRealocacaoRepo, publicados::add);
        recalculo = new RecalculoAchadosOrcamento(condominios, previsaoRepo, previstoRealizado, registro);
        prorrogacao = new BudgetExtensionService(condominios, previsaoRepo, eventoRepo, consulta, publicados::add);
        exercicios = new ServicoExercicios(previsaoRepo, linhaRepo, previstoRealizado, depara, servicoRubricas);
        comparacao = new ServicoComparacao(condominios, previsaoRepo, linhaRepo, poFundoRepo, rubricaRepo,
                linhaRubricaRepo, achadoRepo, previstoRealizado, exercicios);
        indicadores = new ServicoIndicadores(condominios, previsaoRepo, previstoRealizado, exercicios, comparacao);
        ligacaoFundos = new BudgetFundLinkService(condominios, previsaoRepo, linhaRepo, poFundoRepo, fundoRepo,
                eventoRepo,
                consulta, publicados::add);

        ata = arquivo(FileCategory.ATA, "ata-ago-2026-05.pdf");
    }

    /**
     * Faz o papel do {@link DisparoRecalculoAchados}: para cada mudança publicada desde a última chamada, recalcula
     * os achados (como depois do commit). Devolve quantas mudanças foram tratadas.
     */
    public int aposCommit() {
        List<BudgetChanged> mudancas = publicados.stream().filter(BudgetChanged.class::isInstance)
                .map(BudgetChanged.class::cast).toList();
        publicados.clear();
        mudancas.forEach(recalculo::recalcular);
        return mudancas.size();
    }

    /** Arquivo de fluxo da categoria de balancetes, lido (concluído), cobrindo o período. */
    public SourceFile fluxo(String nome, LocalDate inicio, LocalDate fim, int lancamentos) {
        SourceFile a = arquivo(FileCategory.BALANCETE, nome);
        a.complete(FileStatus.CONCLUIDO, "Todas as conferências passaram", "fluxo-protest", inicio, fim, lancamentos);
        return a;
    }

    /** Lê a PO como a GravacaoResultado faria (linhas e conferências gravadas). */
    public Budget lerPo(PoDoPiloto po) {
        SourceFile arquivo = arquivo(FileCategory.PO, "PO-" + UUID.randomUUID() + ".pdf");
        var lidas = po.conferencias();
        for (int i = 0; i < lidas.size(); i++) {
            conferencias.add(new TotalsCheck(arquivo.getId(), i + 1, lidas.get(i)));
        }
        return gravacao.save(arquivo, "po-protest", po.previsao(), lidas);
    }

    /** Lê uma PO qualquer (ex.: a do golden privado) como a GravacaoResultado faria. */
    public Budget lerPo(BudgetData lida, List<TotalsCheckData> lidas) {
        SourceFile arquivo = arquivo(FileCategory.PO, "PO-" + UUID.randomUUID() + ".pdf");
        for (int i = 0; i < lidas.size(); i++) {
            conferencias.add(new TotalsCheck(arquivo.getId(), i + 1, lidas.get(i)));
        }
        return gravacao.save(arquivo, "po-protest", lida, lidas);
    }

    /** PO do piloto lida e confirmada (exercício 05/2026 a 04/2027, 1.3.25 e os dois fundos). */
    public Budget poConfirmada() {
        Budget po = lerPo(PoDoPiloto.padrao());
        confirmacao.confirm(condominioId, po.getId(), pedidoDoPiloto(po), "admin");
        return po;
    }

    /** Débito no fundo Condomínio (fundo ordinário), num fluxo da categoria de balancetes. */
    public LedgerEntry debito(String conta, String nome, String valor, LocalDate data) {
        var lido = new LedgerEntryData(1, lancamentos.size() + 1, data, conta, nome, "", "Teste " + nome,
                BigDecimal.ZERO.setScale(2), new BigDecimal(valor), BigDecimal.ZERO.setScale(2),
                new Enrichment(null, null, null, false, false));
        LedgerEntry l = new LedgerEntry(condominioId, UUID.randomUUID(), ordinario.getId(), lido);
        lancamentos.add(l);
        return l;
    }

    public SourceFile arquivo(FileCategory categoria, String nome) {
        SourceFile a = new SourceFile(condominioId, categoria, nome, "c/" + nome,
                UUID.randomUUID().toString().replace("-", "")
                + "0".repeat(32), 100, "application/pdf", "admin");
        arquivos.put(a.getId(), a);
        return a;
    }

    public Optional<Budget> consultaVigente(java.time.YearMonth mes) {
        return consulta.activeInMonth(condominioId, mes);
    }

    public BudgetLine linha(Budget po, String codigo, int indice) {
        return linhas.stream().filter(l -> l.getBudgetId().equals(po.getId()) && l.getPrintedCode().equals(codigo))
                .sorted(Comparator.comparingInt(BudgetLine::getPosition)).toList().get(indice);
    }

    /** Pedido completo para a PO do piloto: exercício 05/2026 a 04/2027, ata de maio, 1.3.25 e os dois fundos. */
    public BudgetConfirmationRequest pedidoDoPiloto(Budget po) {
        return new BudgetConfirmationRequest("2026-05", "2027-04", ata.getId(), false, LocalDate.of(2026, 5, 20),
                List.of(new EffectiveCodeRequest(linha(po, "1.3.2", 1).getId(), "1.3.25")),
                ligacoesDoPiloto(po), false, false, null);
    }

    public List<FundLinkRequest> ligacoesDoPiloto(Budget po) {
        return List.of(new FundLinkRequest(linha(po, "1.9.1", 0).getId(), reserva.getId()),
                new FundLinkRequest(linha(po, "1.9.2", 0).getId(), obrasInfra.getId()));
    }
}
