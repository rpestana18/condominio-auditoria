package br.com.condominioauditoria.api.orcamento;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import br.com.condominioauditoria.api.model.condominium.Condominium;
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
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.service.audit.FindingSyncService;
import br.com.condominioauditoria.api.service.audit.rule.MonthlyOverrunRule;
import br.com.condominioauditoria.api.service.audit.rule.ReserveFundCapRule;
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
final class CenarioPo {

    final UUID condominioId = UUID.randomUUID();
    final Condominium condominio = mock(Condominium.class);
    final Fund ordinario = new Fund(condominioId, "CONDOMÍNIO");
    final Fund reserva = new Fund(condominioId, "FUNDO DE RESERVA");
    final Fund obrasInfra = new Fund(condominioId, "OBRAS / REFORMAS / INFRA");
    final Fund obras = new Fund(condominioId, "OBRAS");
    final SourceFile ata;

    final Map<UUID, SourceFile> arquivos = new LinkedHashMap<>();
    final Map<UUID, PrevisaoOrcamentaria> previsoes = new LinkedHashMap<>();
    final List<LinhaPo> linhas = new ArrayList<>();
    final List<TotalsCheck> conferencias = new ArrayList<>();
    final List<PoFundo> poFundos = new ArrayList<>();
    final List<EventoPrevisao> eventos = new ArrayList<>();
    final List<Finding> achados = new ArrayList<>();
    final List<FindingEvidence> evidencias = new ArrayList<>();
    final List<RuleParameter> parametros = new ArrayList<>();
    final List<DeparaConta> deparas = new ArrayList<>();
    final List<EventoDepara> eventosDepara = new ArrayList<>();
    final List<LedgerEntry> lancamentos = new ArrayList<>();
    final List<FindingEvent> eventosAchado = new ArrayList<>();
    final List<RealocacaoLancamento> realocacoes = new ArrayList<>();
    final List<EventoRealocacao> eventosRealocacao = new ArrayList<>();
    final List<Fund> fundos = new ArrayList<>();
    final List<Rubrica> rubricas = new ArrayList<>();
    final List<LinhaRubrica> linhasRubrica = new ArrayList<>();
    final List<EventoRubrica> eventosRubrica = new ArrayList<>();
    /** Mudanças publicadas pelos serviços; {@link #aposCommit()} faz o papel do disparo depois do commit. */
    final List<Object> publicados = new ArrayList<>();

    final ConfirmacaoPrevisao confirmacao;
    final ConsultaPrevisao consulta;
    final ServicoDepara depara;
    final ServicoRubricas servicoRubricas;
    final ServicoProrrogacao prorrogacao;
    final ServicoExercicios exercicios;
    final ServicoComparacao comparacao;
    final ServicoIndicadores indicadores;
    final PrevisaoOrcamentariaRepository previsaoRepo;
    final LinhaPoRepository linhaRepo;
    final DeparaContaRepository deparaRepo;
    final EventoDeparaRepository eventoDeparaRepo;
    final ConsultaPrevistoRealizado previstoRealizado;
    final ServicoRealocacao realocacao;
    final RecalculoAchadosOrcamento recalculo;
    final FindingSyncService registro;
    final LigacaoFundosPo ligacaoFundos;
    private final GravacaoPrevisao gravacao;

    CenarioPo() {
        when(condominio.getId()).thenReturn(condominioId);
        when(condominio.getOperatingFundId()).thenReturn(ordinario.getId());
        CondominiumRepository condominios = mock(CondominiumRepository.class);
        when(condominios.lockById(condominioId)).thenReturn(Optional.of(condominio));
        when(condominios.findById(condominioId)).thenReturn(Optional.of(condominio));

        SourceFileRepository arquivoRepo = mock(SourceFileRepository.class);
        when(arquivoRepo.findById(any())).thenAnswer(i -> Optional.ofNullable(arquivos.get(i.<UUID>getArgument(0))));
        when(arquivoRepo.findByIdAndCondominiumId(any(), any())).thenAnswer(i -> Optional
                .ofNullable(arquivos.get(i.<UUID>getArgument(0))).filter(a -> a.getCondominiumId().equals(i.getArgument(1))));

        previsaoRepo = mock(PrevisaoOrcamentariaRepository.class);
        when(previsaoRepo.save(any())).thenAnswer(i -> {
            PrevisaoOrcamentaria p = i.getArgument(0);
            previsoes.put(p.getId(), p);
            return p;
        });
        when(previsaoRepo.findByArquivoId(any())).thenAnswer(i -> previsoes.values().stream()
                .filter(p -> p.getArquivoId().equals(i.getArgument(0))).findFirst());
        when(previsaoRepo.findByIdAndCondominioId(any(), any())).thenAnswer(i -> Optional
                .ofNullable(previsoes.get(i.<UUID>getArgument(0))).filter(p -> p.getCondominioId().equals(i.getArgument(1))));
        when(previsaoRepo.findByCondominioIdAndEstadoIn(any(), any())).thenAnswer(i -> previsoes.values().stream()
                .filter(p -> p.getCondominioId().equals(i.getArgument(0))
                        && i.<Collection<EstadoPrevisao>>getArgument(1).contains(p.getEstado()))
                .toList());

        linhaRepo = mock(LinhaPoRepository.class);
        when(linhaRepo.saveAll(any())).thenAnswer(i -> {
            for (LinhaPo l : i.<Iterable<LinhaPo>>getArgument(0)) {
                if (!linhas.contains(l)) {
                    linhas.add(l);
                }
            }
            return i.getArgument(0);
        });
        when(linhaRepo.findByPrevisaoIdOrderByOrdem(any())).thenAnswer(i -> linhas.stream()
                .filter(l -> l.getPrevisaoId().equals(i.getArgument(0))).sorted(Comparator.comparingInt(LinhaPo::getOrdem))
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

        PoFundoRepository poFundoRepo = mock(PoFundoRepository.class);
        when(poFundoRepo.save(any())).thenAnswer(i -> {
            poFundos.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(poFundoRepo.findByPrevisaoId(any())).thenAnswer(i -> poFundos.stream()
                .filter(f -> f.getPrevisaoId().equals(i.getArgument(0))).toList());
        org.mockito.Mockito.doAnswer(i -> poFundos.removeIf(f -> f.getPrevisaoId().equals(i.getArgument(0))))
                .when(poFundoRepo).apagarDaPrevisao(any());

        EventoPrevisaoRepository eventoRepo = mock(EventoPrevisaoRepository.class);
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
        when(achadoRepo.findByCondominiumIdAndTargetStartingWithOrderByCreatedAt(any(), anyString())).thenAnswer(i -> achados
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

        ReservaDaPo reservaDaPo = new ReservaDaPo(parametroRepo);
        consulta = new ConsultaPrevisao(previsaoRepo, linhaRepo, conferenciaRepo, arquivoRepo, poFundoRepo, fundoRepo,
                achadoRepo, reservaDaPo);
        registro = new FindingSyncService(achadoRepo, evidenciaRepo, eventoAchadoRepo);
        RubricaRepository rubricaRepo = mock(RubricaRepository.class);
        when(rubricaRepo.save(any())).thenAnswer(i -> {
            if (!rubricas.contains(i.<Rubrica>getArgument(0))) {
                rubricas.add(i.getArgument(0));
            }
            return i.getArgument(0);
        });
        when(rubricaRepo.existsByCondominioId(any())).thenAnswer(i -> rubricas.stream()
                .anyMatch(r -> r.getCondominioId().equals(i.getArgument(0))));
        when(rubricaRepo.findByCondominioIdOrderByNome(any())).thenAnswer(i -> rubricas.stream()
                .filter(r -> r.getCondominioId().equals(i.getArgument(0))).sorted(Comparator.comparing(Rubrica::getNome))
                .toList());
        when(rubricaRepo.findById(any())).thenAnswer(i -> rubricas.stream()
                .filter(r -> r.getId().equals(i.getArgument(0))).findFirst());
        when(rubricaRepo.findByIdAndCondominioId(any(), any())).thenAnswer(i -> rubricas.stream()
                .filter(r -> r.getId().equals(i.getArgument(0)) && r.getCondominioId().equals(i.getArgument(1)))
                .findFirst());
        LinhaRubricaRepository linhaRubricaRepo = mock(LinhaRubricaRepository.class);
        when(linhaRubricaRepo.save(any())).thenAnswer(i -> {
            if (!linhasRubrica.contains(i.<LinhaRubrica>getArgument(0))) {
                linhasRubrica.add(i.getArgument(0));
            }
            return i.getArgument(0);
        });
        when(linhaRubricaRepo.findByPrevisaoId(any())).thenAnswer(i -> linhasRubrica.stream()
                .filter(l -> l.getPrevisaoId().equals(i.getArgument(0))).toList());
        when(linhaRubricaRepo.findByCondominioIdAndEstado(any(), any())).thenAnswer(i -> linhasRubrica.stream()
                .filter(l -> l.getCondominioId().equals(i.getArgument(0)) && l.getEstado() == i.getArgument(1))
                .toList());
        EventoRubricaRepository eventoRubricaRepo = mock(EventoRubricaRepository.class);
        when(eventoRubricaRepo.save(any())).thenAnswer(i -> {
            eventosRubrica.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(eventoRubricaRepo.findByPrevisaoIdOrderByEmAscLinhaCodigoAsc(any())).thenAnswer(i -> eventosRubrica.stream()
                .filter(e -> i.getArgument(0).equals(e.getPrevisaoId())).toList());
        servicoRubricas = new ServicoRubricas(condominios, previsaoRepo, linhaRepo, rubricaRepo, linhaRubricaRepo,
                eventoRubricaRepo);
        confirmacao = new ConfirmacaoPrevisao(condominios, previsaoRepo, linhaRepo, poFundoRepo, eventoRepo,
                arquivoRepo, fundoRepo, consulta, reservaDaPo, registro, servicoRubricas, publicados::add);
        gravacao = new GravacaoPrevisao(previsaoRepo, linhaRepo, new PropriedadesOrcamento(new BigDecimal("0.01")));

        deparaRepo = mock(DeparaContaRepository.class);
        when(deparaRepo.save(any())).thenAnswer(i -> {
            DeparaConta d = i.getArgument(0);
            if (!deparas.contains(d)) {
                deparas.add(d);
            }
            return d;
        });
        when(deparaRepo.findByPrevisaoIdOrderByContaCodigo(any())).thenAnswer(i -> deparas.stream()
                .filter(d -> d.getPrevisaoId().equals(i.getArgument(0)))
                .sorted(Comparator.comparing(DeparaConta::getContaCodigo)).toList());
        when(deparaRepo.findByPrevisaoIdAndContaCodigo(any(), anyString())).thenAnswer(i -> deparas.stream()
                .filter(d -> d.getPrevisaoId().equals(i.getArgument(0)) && d.getContaCodigo().equals(i.getArgument(1)))
                .findFirst());
        eventoDeparaRepo = mock(EventoDeparaRepository.class);
        when(eventoDeparaRepo.save(any())).thenAnswer(i -> {
            eventosDepara.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(eventoDeparaRepo.findByPrevisaoIdOrderByEmAscContaCodigoAsc(any())).thenAnswer(i -> eventosDepara.stream()
                .filter(e -> e.getPrevisaoId().equals(i.getArgument(0))).toList());
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
        depara = new ServicoDepara(condominios, previsaoRepo, linhaRepo, deparaRepo, eventoDeparaRepo, lancamentoRepo,
                PropriedadesDepara.padrao(), publicados::add);

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
        prorrogacao = new ServicoProrrogacao(condominios, previsaoRepo, eventoRepo, consulta, publicados::add);
        exercicios = new ServicoExercicios(previsaoRepo, linhaRepo, previstoRealizado, depara, servicoRubricas);
        comparacao = new ServicoComparacao(condominios, previsaoRepo, linhaRepo, poFundoRepo, rubricaRepo,
                linhaRubricaRepo, achadoRepo, previstoRealizado, exercicios);
        indicadores = new ServicoIndicadores(condominios, previsaoRepo, previstoRealizado, exercicios, comparacao);
        ligacaoFundos = new LigacaoFundosPo(condominios, previsaoRepo, linhaRepo, poFundoRepo, fundoRepo, eventoRepo,
                consulta, publicados::add);

        ata = arquivo(FileCategory.ATA, "ata-ago-2026-05.pdf");
    }

    /**
     * Faz o papel do {@link DisparoRecalculoAchados}: para cada mudança publicada desde a última chamada, recalcula
     * os achados (como depois do commit). Devolve quantas mudanças foram tratadas.
     */
    int aposCommit() {
        List<MudancaOrcamento> mudancas = publicados.stream().filter(MudancaOrcamento.class::isInstance)
                .map(MudancaOrcamento.class::cast).toList();
        publicados.clear();
        mudancas.forEach(recalculo::recalcular);
        return mudancas.size();
    }

    /** Arquivo de fluxo da categoria de balancetes, lido (concluído), cobrindo o período. */
    SourceFile fluxo(String nome, LocalDate inicio, LocalDate fim, int lancamentos) {
        SourceFile a = arquivo(FileCategory.BALANCETE, nome);
        a.complete(FileStatus.CONCLUIDO, "Todas as conferências passaram", "fluxo-protest", inicio, fim, lancamentos);
        return a;
    }

    /** Lê a PO como a GravacaoResultado faria (linhas e conferências gravadas). */
    PrevisaoOrcamentaria lerPo(PoDoPiloto po) {
        SourceFile arquivo = arquivo(FileCategory.PO, "PO-" + UUID.randomUUID() + ".pdf");
        var lidas = po.conferencias();
        for (int i = 0; i < lidas.size(); i++) {
            conferencias.add(new TotalsCheck(arquivo.getId(), i + 1, lidas.get(i)));
        }
        return gravacao.gravar(arquivo, "po-protest", po.previsao(), lidas);
    }

    /** Lê uma PO qualquer (ex.: a do golden privado) como a GravacaoResultado faria. */
    PrevisaoOrcamentaria lerPo(BudgetData lida, List<TotalsCheckData> lidas) {
        SourceFile arquivo = arquivo(FileCategory.PO, "PO-" + UUID.randomUUID() + ".pdf");
        for (int i = 0; i < lidas.size(); i++) {
            conferencias.add(new TotalsCheck(arquivo.getId(), i + 1, lidas.get(i)));
        }
        return gravacao.gravar(arquivo, "po-protest", lida, lidas);
    }

    /** PO do piloto lida e confirmada (exercício 05/2026 a 04/2027, 1.3.25 e os dois fundos). */
    PrevisaoOrcamentaria poConfirmada() {
        PrevisaoOrcamentaria po = lerPo(PoDoPiloto.padrao());
        confirmacao.confirmar(condominioId, po.getId(), pedidoDoPiloto(po), "admin");
        return po;
    }

    /** Débito no fundo Condomínio (fundo ordinário), num fluxo da categoria de balancetes. */
    LedgerEntry debito(String conta, String nome, String valor, LocalDate data) {
        var lido = new LedgerEntryData(1, lancamentos.size() + 1, data, conta, nome, "", "Teste " + nome,
                BigDecimal.ZERO.setScale(2), new BigDecimal(valor), BigDecimal.ZERO.setScale(2),
                new Enrichment(null, null, null, false, false));
        LedgerEntry l = new LedgerEntry(condominioId, UUID.randomUUID(), ordinario.getId(), lido);
        lancamentos.add(l);
        return l;
    }

    SourceFile arquivo(FileCategory categoria, String nome) {
        SourceFile a = new SourceFile(condominioId, categoria, nome, "c/" + nome, UUID.randomUUID().toString().replace("-", "")
                + "0".repeat(32), 100, "application/pdf", "admin");
        arquivos.put(a.getId(), a);
        return a;
    }

    Optional<PrevisaoOrcamentaria> consultaVigente(java.time.YearMonth mes) {
        return consulta.vigenteNoMes(condominioId, mes);
    }

    LinhaPo linha(PrevisaoOrcamentaria po, String codigo, int indice) {
        return linhas.stream().filter(l -> l.getPrevisaoId().equals(po.getId()) && l.getCodigoImpresso().equals(codigo))
                .sorted(Comparator.comparingInt(LinhaPo::getOrdem)).toList().get(indice);
    }

    /** Pedido completo para a PO do piloto: exercício 05/2026 a 04/2027, ata de maio, 1.3.25 e os dois fundos. */
    PedidoConfirmacao pedidoDoPiloto(PrevisaoOrcamentaria po) {
        return new PedidoConfirmacao("2026-05", "2027-04", ata.getId(), false, LocalDate.of(2026, 5, 20),
                List.of(new PedidoConfirmacao.CodigoEfetivo(linha(po, "1.3.2", 1).getId(), "1.3.25")),
                ligacoesDoPiloto(po), false, false, null);
    }

    List<PedidoConfirmacao.LigacaoFundo> ligacoesDoPiloto(PrevisaoOrcamentaria po) {
        return List.of(new PedidoConfirmacao.LigacaoFundo(linha(po, "1.9.1", 0).getId(), reserva.getId()),
                new PedidoConfirmacao.LigacaoFundo(linha(po, "1.9.2", 0).getId(), obrasInfra.getId()));
    }
}
