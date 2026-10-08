package br.com.condominioauditoria.backend.orcamento;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import br.com.condominioauditoria.backend.arquivo.ArquivoRepository;
import br.com.condominioauditoria.backend.arquivo.Categoria;
import br.com.condominioauditoria.backend.arquivo.StatusArquivo;
import br.com.condominioauditoria.backend.auditoria.Achado;
import br.com.condominioauditoria.backend.auditoria.AchadoEvidencia;
import br.com.condominioauditoria.backend.auditoria.AchadoEvidenciaRepository;
import br.com.condominioauditoria.backend.auditoria.AchadoRepository;
import br.com.condominioauditoria.backend.auditoria.EventoAchado;
import br.com.condominioauditoria.backend.auditoria.EventoAchadoRepository;
import br.com.condominioauditoria.backend.auditoria.RegraExcessoMes;
import br.com.condominioauditoria.backend.auditoria.ParametroRegra;
import br.com.condominioauditoria.backend.auditoria.ParametroRegraRepository;
import br.com.condominioauditoria.backend.auditoria.RegistroAchados;
import br.com.condominioauditoria.backend.auditoria.RegraTetoFundoReserva;
import br.com.condominioauditoria.backend.condominio.Condominio;
import br.com.condominioauditoria.backend.condominio.CondominioRepository;
import br.com.condominioauditoria.backend.contabil.Conferencia;
import br.com.condominioauditoria.backend.contabil.ConferenciaRepository;
import br.com.condominioauditoria.backend.contabil.Fundo;
import br.com.condominioauditoria.backend.contabil.FundoRepository;
import br.com.condominioauditoria.backend.contabil.Lancamento;
import br.com.condominioauditoria.backend.contabil.LancamentoRepository;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.ConferenciaLida;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.Enriquecimento;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.LancamentoLido;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.PrevisaoLida;
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
    final Condominio condominio = mock(Condominio.class);
    final Fundo ordinario = new Fundo(condominioId, "CONDOMÍNIO");
    final Fundo reserva = new Fundo(condominioId, "FUNDO DE RESERVA");
    final Fundo obrasInfra = new Fundo(condominioId, "OBRAS / REFORMAS / INFRA");
    final Fundo obras = new Fundo(condominioId, "OBRAS");
    final Arquivo ata;

    final Map<UUID, Arquivo> arquivos = new LinkedHashMap<>();
    final Map<UUID, PrevisaoOrcamentaria> previsoes = new LinkedHashMap<>();
    final List<LinhaPo> linhas = new ArrayList<>();
    final List<Conferencia> conferencias = new ArrayList<>();
    final List<PoFundo> poFundos = new ArrayList<>();
    final List<EventoPrevisao> eventos = new ArrayList<>();
    final List<Achado> achados = new ArrayList<>();
    final List<AchadoEvidencia> evidencias = new ArrayList<>();
    final List<ParametroRegra> parametros = new ArrayList<>();
    final List<DeparaConta> deparas = new ArrayList<>();
    final List<EventoDepara> eventosDepara = new ArrayList<>();
    final List<Lancamento> lancamentos = new ArrayList<>();
    final List<EventoAchado> eventosAchado = new ArrayList<>();
    final List<RealocacaoLancamento> realocacoes = new ArrayList<>();
    final List<EventoRealocacao> eventosRealocacao = new ArrayList<>();
    final List<Fundo> fundos = new ArrayList<>();
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
    final PrevisaoOrcamentariaRepository previsaoRepo;
    final LinhaPoRepository linhaRepo;
    final DeparaContaRepository deparaRepo;
    final EventoDeparaRepository eventoDeparaRepo;
    final ConsultaPrevistoRealizado previstoRealizado;
    final ServicoRealocacao realocacao;
    final RecalculoAchadosOrcamento recalculo;
    final RegistroAchados registro;
    final LigacaoFundosPo ligacaoFundos;
    private final GravacaoPrevisao gravacao;

    CenarioPo() {
        when(condominio.getId()).thenReturn(condominioId);
        when(condominio.getFundoOrdinarioId()).thenReturn(ordinario.getId());
        CondominioRepository condominios = mock(CondominioRepository.class);
        when(condominios.travar(condominioId)).thenReturn(Optional.of(condominio));
        when(condominios.findById(condominioId)).thenReturn(Optional.of(condominio));

        ArquivoRepository arquivoRepo = mock(ArquivoRepository.class);
        when(arquivoRepo.findById(any())).thenAnswer(i -> Optional.ofNullable(arquivos.get(i.<UUID>getArgument(0))));
        when(arquivoRepo.findByIdAndCondominioId(any(), any())).thenAnswer(i -> Optional
                .ofNullable(arquivos.get(i.<UUID>getArgument(0))).filter(a -> a.getCondominioId().equals(i.getArgument(1))));

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

        ConferenciaRepository conferenciaRepo = mock(ConferenciaRepository.class);
        when(conferenciaRepo.findByArquivoIdOrderByOrdem(any())).thenAnswer(i -> conferencias.stream()
                .filter(c -> c.getArquivoId().equals(i.getArgument(0))).toList());

        FundoRepository fundoRepo = mock(FundoRepository.class);
        fundos.addAll(List.of(ordinario, reserva, obrasInfra, obras));
        when(fundoRepo.findByCondominioId(condominioId)).thenAnswer(i -> List.copyOf(fundos));
        when(arquivoRepo.findByCondominioIdAndCategoriaAndStatusIn(any(), any(), any())).thenAnswer(i -> arquivos
                .values().stream().filter(a -> a.getCondominioId().equals(i.getArgument(0))
                        && a.getCategoria() == i.getArgument(1)
                        && i.<Collection<StatusArquivo>>getArgument(2).contains(a.getStatus()))
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

        AchadoRepository achadoRepo = mock(AchadoRepository.class);
        when(achadoRepo.save(any())).thenAnswer(i -> {
            if (!achados.contains(i.<Achado>getArgument(0))) {
                achados.add(i.getArgument(0));
            }
            return i.getArgument(0);
        });
        when(achadoRepo.findByCondominioIdAndCompetenciaAndRegraIn(any(), any(), any())).thenAnswer(i -> achados
                .stream().filter(a -> a.getCondominioId().equals(i.getArgument(0))
                        && a.getCompetencia().atDay(1).equals(i.getArgument(1))
                        && i.<Collection<String>>getArgument(2).contains(a.getRegra()))
                .toList());
        EventoAchadoRepository eventoAchadoRepo = mock(EventoAchadoRepository.class);
        when(eventoAchadoRepo.save(any())).thenAnswer(i -> {
            eventosAchado.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(achadoRepo.findByCondominioIdAndRegraAndCompetenciaAndAlvo(any(), anyString(), any(), anyString()))
                .thenAnswer(i -> achados.stream().filter(a -> a.getCondominioId().equals(i.getArgument(0))
                        && a.getRegra().equals(i.getArgument(1))
                        && a.getCompetencia().atDay(1).equals(i.getArgument(2)) && a.getAlvo().equals(i.getArgument(3)))
                        .findFirst());
        when(achadoRepo.findByCondominioIdAndAlvoStartingWithOrderByCriadoEm(any(), anyString())).thenAnswer(i -> achados
                .stream().filter(a -> a.getCondominioId().equals(i.getArgument(0))
                        && a.getAlvo().startsWith(i.getArgument(1))).toList());
        when(achadoRepo.findByCondominioIdOrderByCompetenciaDescCriadoEmAsc(any())).thenAnswer(i -> achados.stream()
                .filter(a -> a.getCondominioId().equals(i.getArgument(0))).toList());
        AchadoEvidenciaRepository evidenciaRepo = mock(AchadoEvidenciaRepository.class);
        when(evidenciaRepo.save(any())).thenAnswer(i -> {
            evidencias.add(i.getArgument(0));
            return i.getArgument(0);
        });

        ParametroRegraRepository parametroRepo = mock(ParametroRegraRepository.class);
        when(parametroRepo.vigente(any(), anyString(), any())).thenAnswer(i -> parametros.stream()
                .filter(p -> p.getCodigo().equals(i.getArgument(1))
                        && !p.getVigenteDesde().isAfter(i.getArgument(2)))
                .findFirst());
        parametros.add(new ParametroRegra(condominioId, RegraTetoFundoReserva.PARAMETRO, new BigDecimal("5.0000"),
                LocalDate.of(1900, 1, 1), null, "Conv. 20.1"));
        parametros.add(new ParametroRegra(condominioId, RegraExcessoMes.PARAMETRO, new BigDecimal("20.0000"),
                LocalDate.of(1900, 1, 1), null, "Conv. 16.2"));

        ReservaDaPo reservaDaPo = new ReservaDaPo(parametroRepo);
        consulta = new ConsultaPrevisao(previsaoRepo, linhaRepo, conferenciaRepo, arquivoRepo, poFundoRepo, fundoRepo,
                achadoRepo, reservaDaPo);
        registro = new RegistroAchados(achadoRepo, evidenciaRepo, eventoAchadoRepo);
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
        LancamentoRepository lancamentoRepo = mock(LancamentoRepository.class);
        when(lancamentoRepo.debitosComConta(any(), any(), any(), any())).thenAnswer(i -> lancamentos.stream()
                .filter(l -> l.getFundoId().equals(i.getArgument(1)) && !l.getData().isBefore(i.getArgument(2))
                        && !l.getData().isAfter(i.getArgument(3)) && l.getDebito().signum() != 0
                        && !l.isTransferenciaEntreFundos() && l.getContaCodigo() != null)
                .sorted(Comparator.comparing(Lancamento::getData)).toList());
        when(lancamentoRepo.findByArquivoIdInAndDataBetween(any(), any(), any())).thenAnswer(i -> lancamentos.stream()
                .filter(l -> i.<Collection<UUID>>getArgument(0).contains(l.getArquivoId())
                        && !l.getData().isBefore(i.getArgument(1)) && !l.getData().isAfter(i.getArgument(2)))
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
        ligacaoFundos = new LigacaoFundosPo(condominios, previsaoRepo, linhaRepo, poFundoRepo, fundoRepo, eventoRepo,
                consulta, publicados::add);

        ata = arquivo(Categoria.ATA, "ata-ago-2026-05.pdf");
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
    Arquivo fluxo(String nome, LocalDate inicio, LocalDate fim, int lancamentos) {
        Arquivo a = arquivo(Categoria.BALANCETE, nome);
        a.concluir(StatusArquivo.CONCLUIDO, "Todas as conferências passaram", "fluxo-protest", inicio, fim, lancamentos);
        return a;
    }

    /** Lê a PO como a GravacaoResultado faria (linhas e conferências gravadas). */
    PrevisaoOrcamentaria lerPo(PoDoPiloto po) {
        Arquivo arquivo = arquivo(Categoria.PO, "PO-" + UUID.randomUUID() + ".pdf");
        var lidas = po.conferencias();
        for (int i = 0; i < lidas.size(); i++) {
            conferencias.add(new Conferencia(arquivo.getId(), i + 1, lidas.get(i)));
        }
        return gravacao.gravar(arquivo, "po-protest", po.previsao(), lidas);
    }

    /** Lê uma PO qualquer (ex.: a do golden privado) como a GravacaoResultado faria. */
    PrevisaoOrcamentaria lerPo(PrevisaoLida lida, List<ConferenciaLida> lidas) {
        Arquivo arquivo = arquivo(Categoria.PO, "PO-" + UUID.randomUUID() + ".pdf");
        for (int i = 0; i < lidas.size(); i++) {
            conferencias.add(new Conferencia(arquivo.getId(), i + 1, lidas.get(i)));
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
    Lancamento debito(String conta, String nome, String valor, LocalDate data) {
        var lido = new LancamentoLido(1, lancamentos.size() + 1, data, conta, nome, "", "Teste " + nome,
                BigDecimal.ZERO.setScale(2), new BigDecimal(valor), BigDecimal.ZERO.setScale(2),
                new Enriquecimento(null, null, null, false, false));
        Lancamento l = new Lancamento(condominioId, UUID.randomUUID(), ordinario.getId(), lido);
        lancamentos.add(l);
        return l;
    }

    Arquivo arquivo(Categoria categoria, String nome) {
        Arquivo a = new Arquivo(condominioId, categoria, nome, "c/" + nome, UUID.randomUUID().toString().replace("-", "")
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
