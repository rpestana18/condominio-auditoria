package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.AcaoLote;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.ContaDepara;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.ContaIgnorada;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.ContaSemSugestao;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.DeparaLista;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.DestinoDto;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.EventoDeparaDto;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.FiltroDepara;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.LinhaRecusada;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.PedidoDestino;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.PedidoLote;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.ResultadoLote;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.ResultadoPlanilha;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.ResultadoSugestoes;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.ResumoDepara;
import br.com.condominioauditoria.api.repository.accounting.LedgerEntryRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * De-para das contas do fluxo para a PO (RF-03.1.4 e RF-03.1.5; ADR 0004, Decisão 4), por versão confirmada da PO.
 * Sugestões (versão anterior, planilha, nome) entram sempre como SUGERIDO e não alteram número nenhum: o previsto ×
 * realizado só usa o que o Admin confirmou. Toda mudança grava um evento na trilha (só de inserção).
 *
 * <p>Nenhuma chamada de IA: a sugestão pelo nome é a função pura {@link SugestaoPorNome}.
 */
@Service
public class ServicoDepara {

    private static final Logger log = LoggerFactory.getLogger(ServicoDepara.class);
    private static final Pattern CONTA = Pattern.compile("^\\d{1,20}$");

    private final CondominiumRepository condominios;
    private final PrevisaoOrcamentariaRepository previsoes;
    private final LinhaPoRepository linhas;
    private final DeparaContaRepository deparas;
    private final EventoDeparaRepository eventos;
    private final LedgerEntryRepository lancamentos;
    private final SugestaoPorNome sugestao;
    private final ApplicationEventPublisher publicador;

    ServicoDepara(CondominiumRepository condominios, PrevisaoOrcamentariaRepository previsoes, LinhaPoRepository linhas,
            DeparaContaRepository deparas, EventoDeparaRepository eventos, LedgerEntryRepository lancamentos,
            PropriedadesDepara propriedades, ApplicationEventPublisher publicador) {
        this.condominios = condominios;
        this.previsoes = previsoes;
        this.linhas = linhas;
        this.deparas = deparas;
        this.eventos = eventos;
        this.lancamentos = lancamentos;
        this.sugestao = new SugestaoPorNome(propriedades);
        this.publicador = publicador;
    }

    /** Conta do fluxo com débito no fundo Condomínio no exercício da PO: nome impresso, quantidade e soma. */
    record ContaDoFluxo(String codigo, String nome, int lancamentos, BigDecimal debitos) {
    }

    /** PO, linhas e destinos possíveis, carregados uma vez por pedido. */
    private record Contexto(PrevisaoOrcamentaria po, List<LinhaPo> linhas, EstruturaPo estrutura,
            Map<UUID, LinhaPo> porId, Map<String, LinhaPo> destinosPorCodigo) {
    }

    @Transactional(readOnly = true)
    public DeparaLista listar(UUID condominioId, UUID poId, FiltroDepara filtro) {
        PrevisaoOrcamentaria po = po(condominioId, poId);
        Contexto ctx = contexto(po);
        Map<String, ContaDoFluxo> doFluxo = po.getEstado().travada() ? contasDoFluxo(po) : Map.of();
        Map<String, DeparaConta> existentes = porConta(deparas.findByPrevisaoIdOrderByContaCodigo(po.getId()));

        TreeMap<String, ContaDepara> contas = new TreeMap<>();
        doFluxo.forEach((codigo, c) -> contas.put(codigo, conta(c, existentes.get(codigo), ctx)));
        existentes.forEach((codigo, d) -> contas.computeIfAbsent(codigo, k -> conta(
                new ContaDoFluxo(codigo, d.getContaNome(), 0, BigDecimal.ZERO.setScale(2)), d, ctx)));

        List<ContaDepara> todas = List.copyOf(contas.values());
        ResumoDepara resumo = new ResumoDepara(todas.size(), contar(todas, EstadoDepara.CONFIRMADO),
                contar(todas, EstadoDepara.SUGERIDO), contar(todas, EstadoDepara.RECUSADO),
                (int) todas.stream().filter(c -> c.estado() == null).count());
        FiltroDepara f = filtro == null ? FiltroDepara.TODAS : filtro;
        return new DeparaLista(po.getId(), po.getVersao(), resumo, todas.stream().filter(c -> passa(c, f)).toList());
    }

    /** Admin escolhe o destino de uma conta (na lista de linhas da PO ou um destino especial). */
    @Transactional
    public ContaDepara definir(UUID condominioId, UUID poId, String conta, PedidoDestino pedido, String usuario) {
        Contexto ctx = contextoParaEscrita(condominioId, poId);
        String codigo = validarConta(conta);
        if (pedido == null || pedido.tipo() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Informe o tipo de destino");
        }
        Destino destino;
        if (pedido.tipo() == TipoDestino.LINHA_PO) {
            LinhaPo l = pedido.linhaId() == null ? null : ctx.porId().get(pedido.linhaId());
            if (l == null || !ctx.destinosPorCodigo().containsValue(l)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, l == null
                        ? "A linha informada não é desta PO"
                        : "A linha " + l.getCodigoEfetivo() + " não recebe débitos: as linhas 1.9 são comparadas com"
                                + " a arrecadação do fundo e as linhas de total e grupo não têm lançamento");
            }
            destino = Destino.linha(l);
        } else {
            if (pedido.linhaId() != null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "Destino " + pedido.tipo() + " não tem linha da PO");
            }
            destino = Destino.especial(pedido.tipo(), pedido.detalhe());
        }
        EstadoDepara estado = pedido.confirmar() == null || pedido.confirmar() ? EstadoDepara.CONFIRMADO
                : EstadoDepara.SUGERIDO;
        String nome = Optional.ofNullable(contasDoFluxo(ctx.po()).get(codigo)).map(ContaDoFluxo::nome).orElse(null);
        Instant agora = Instant.now();
        DeparaConta d = gravar(ctx, codigo, nome, destino, estado, OrigemDepara.ADMIN, "escolhido pelo Admin", false,
                usuario, agora, true);
        if (agora.equals(d.getAtualizadoEm())) {
            publicador.publishEvent(MudancaOrcamento.de(condominioId, "de-para da conta " + codigo + " "
                    + (estado == EstadoDepara.CONFIRMADO ? "confirmado" : "sugerido"), usuario, agora));
        }
        return conta(Optional.ofNullable(contasDoFluxo(ctx.po()).get(codigo))
                .orElse(new ContaDoFluxo(codigo, nome, 0, BigDecimal.ZERO.setScale(2))), d, ctx);
    }

    /** Confirmar ou recusar em lote (RF-03.1.13), um evento por conta alterada. */
    @Transactional
    public ResultadoLote lote(UUID condominioId, UUID poId, PedidoLote pedido, String usuario) {
        Contexto ctx = contextoParaEscrita(condominioId, poId);
        if (pedido == null || pedido.acao() == null || pedido.contas() == null || pedido.contas().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Informe a ação e as contas");
        }
        Map<String, DeparaConta> existentes = porConta(deparas.findByPrevisaoIdOrderByContaCodigo(ctx.po().getId()));
        EstadoDepara novo = pedido.acao() == AcaoLote.CONFIRMAR ? EstadoDepara.CONFIRMADO : EstadoDepara.RECUSADO;
        Instant agora = Instant.now();
        int alteradas = 0;
        List<ContaIgnorada> ignoradas = new ArrayList<>();
        for (String conta : pedido.contas().stream().distinct().toList()) {
            DeparaConta d = existentes.get(conta);
            if (d == null) {
                ignoradas.add(new ContaIgnorada(conta, "conta sem de-para nesta versão: escolha o destino"));
            } else if (d.getEstado() == novo) {
                ignoradas.add(new ContaIgnorada(conta, "já está " + novo.name().toLowerCase()));
            } else {
                Destino destino = d.destino(ctx.porId());
                EstadoDepara antes = d.getEstado();
                d.mudarEstado(novo, usuario, agora);
                deparas.save(d);
                eventos.save(new EventoDepara(d, novo == EstadoDepara.CONFIRMADO ? EventoDepara.Acao.CONFIRMADO
                        : EventoDepara.Acao.RECUSADO, destino, antes, destino, usuario, agora));
                alteradas++;
            }
        }
        log.info("De-para da PO {}: {} conta(s) {} por {}", ctx.po().getId(), alteradas, novo, usuario);
        if (alteradas > 0) {
            String acao = novo == EstadoDepara.CONFIRMADO ? "confirmado" : "recusado";
            List<String> mudadas = pedido.contas().stream().distinct()
                    .filter(c -> ignoradas.stream().noneMatch(i -> i.conta().equals(c))).toList();
            publicador.publishEvent(MudancaOrcamento.de(condominioId, mudadas.size() == 1
                    ? "de-para da conta " + mudadas.getFirst() + " " + acao
                    : "de-para de " + mudadas.size() + " contas " + acao, usuario, agora));
        }
        return new ResultadoLote(alteradas, ignoradas);
    }

    /**
     * Gera sugestões para as contas do fluxo que ainda não têm de-para nesta versão: primeiro a cópia da versão
     * anterior (só o que estava confirmado), depois o nome. Nada é confirmado.
     */
    @Transactional
    public ResultadoSugestoes sugerir(UUID condominioId, UUID poId, String usuario) {
        Contexto ctx = contextoParaEscrita(condominioId, poId);
        Map<String, ContaDoFluxo> doFluxo = contasDoFluxo(ctx.po());
        Map<String, DeparaConta> existentes = porConta(deparas.findByPrevisaoIdOrderByContaCodigo(ctx.po().getId()));
        Instant agora = Instant.now();
        List<LinhaPo> candidatas = SugestaoPorNome.candidatas(ctx.estrutura());

        Map<String, String> motivoAnterior = new LinkedHashMap<>();
        int daAnterior = 0;
        int peloNome = 0;
        Optional<PrevisaoOrcamentaria> anterior = versaoAnterior(ctx.po());
        if (anterior.isPresent()) {
            Map<UUID, LinhaPo> linhasAnteriores = linhas.findByPrevisaoIdOrderByOrdem(anterior.get().getId()).stream()
                    .collect(Collectors.toMap(LinhaPo::getId, Function.identity()));
            for (DeparaConta a : deparas.findByPrevisaoIdOrderByContaCodigo(anterior.get().getId())) {
                if (a.getEstado() != EstadoDepara.CONFIRMADO || existentes.containsKey(a.getContaCodigo())) {
                    continue;
                }
                var copia = CopiaVersaoAnterior.copiar(a, linhasAnteriores, ctx.destinosPorCodigo());
                if (copia instanceof CopiaVersaoAnterior.Copiada c) {
                    String nome = Optional.ofNullable(doFluxo.get(a.getContaCodigo())).map(ContaDoFluxo::nome)
                            .orElse(a.getContaNome());
                    existentes.put(a.getContaCodigo(), gravar(ctx, a.getContaCodigo(), nome, c.destino(),
                            EstadoDepara.SUGERIDO, OrigemDepara.VERSAO_ANTERIOR, c.motivo(), c.igual(), usuario, agora,
                            false));
                    daAnterior++;
                } else {
                    motivoAnterior.put(a.getContaCodigo(), copia.motivo());
                }
            }
        }

        List<ContaSemSugestao> semSugestao = new ArrayList<>();
        Map<String, String> pendentes = new TreeMap<>();
        doFluxo.forEach((codigo, c) -> pendentes.put(codigo, c.nome()));
        motivoAnterior.keySet().forEach(codigo -> pendentes.putIfAbsent(codigo, null));
        for (var e : pendentes.entrySet()) {
            String codigo = e.getKey();
            if (existentes.containsKey(codigo)) {
                continue;
            }
            String prefixo = motivoAnterior.containsKey(codigo) ? motivoAnterior.get(codigo) + "; " : "";
            var r = sugestao.sugerir(e.getValue(), candidatas);
            if (r instanceof SugestaoPorNome.Sugerida s) {
                existentes.put(codigo, gravar(ctx, codigo, e.getValue(), Destino.linha(s.linha()), EstadoDepara.SUGERIDO,
                        OrigemDepara.NOME, prefixo + s.motivo(), false, usuario, agora, false));
                peloNome++;
            } else {
                semSugestao.add(new ContaSemSugestao(codigo, e.getValue(), prefixo + r.motivo()));
            }
        }
        log.info("De-para da PO {}: {} sugestões da versão anterior, {} pelo nome, {} sem sugestão", ctx.po().getId(),
                daAnterior, peloNome, semSugestao.size());
        return new ResultadoSugestoes(daAnterior + peloNome, daAnterior, peloNome, semSugestao);
    }

    /** Planilha de sugestões (CSV): tudo entra como SUGERIDO; conta já confirmada não muda. */
    @Transactional
    public ResultadoPlanilha carregarPlanilha(UUID condominioId, UUID poId, String nomeArquivo, String conteudo,
            String usuario) {
        Contexto ctx = contextoParaEscrita(condominioId, poId);
        List<LinhaPo> deFundo = ctx.estrutura().fundos().map(EstruturaPo.Grupo::linhas).orElse(List.of());
        var leitura = PlanilhaDepara.ler(conteudo, List.copyOf(ctx.destinosPorCodigo().values()), deFundo);
        Map<String, ContaDoFluxo> doFluxo = contasDoFluxo(ctx.po());
        Map<String, DeparaConta> existentes = porConta(deparas.findByPrevisaoIdOrderByContaCodigo(ctx.po().getId()));
        Instant agora = Instant.now();
        String origem = nomeArquivo == null || nomeArquivo.isBlank() ? "planilha" : "planilha " + nomeArquivo;
        int aceitas = 0;
        List<ContaIgnorada> ignoradas = new ArrayList<>();
        for (PlanilhaDepara.Item item : leitura.itens()) {
            DeparaConta atual = existentes.get(item.conta());
            if (atual != null && atual.getEstado() == EstadoDepara.CONFIRMADO) {
                ignoradas.add(new ContaIgnorada(item.conta(), "já confirmada (" + atual.destino(ctx.porId()).texto()
                        + "): para mudar, altere a conta"));
                continue;
            }
            String nome = Optional.ofNullable(doFluxo.get(item.conta())).map(ContaDoFluxo::nome).orElse(null);
            gravar(ctx, item.conta(), nome, item.destino(), EstadoDepara.SUGERIDO, OrigemDepara.PLANILHA,
                    origem + ", linha " + item.linha(), false, usuario, agora, false);
            aceitas++;
        }
        log.info("Planilha do de-para da PO {}: {} aceitas, {} ignoradas, {} recusadas", ctx.po().getId(), aceitas,
                ignoradas.size(), leitura.recusas().size());
        return new ResultadoPlanilha(aceitas, ignoradas, leitura.recusas().stream()
                .map(r -> new LinhaRecusada(r.linha(), r.conteudo(), r.motivo())).toList());
    }

    @Transactional(readOnly = true)
    public List<EventoDeparaDto> eventos(UUID condominioId, UUID poId) {
        return eventos.findByPrevisaoIdOrderByEmAscContaCodigoAsc(po(condominioId, poId).getId()).stream()
                .map(EventoDeparaDto::de).toList();
    }

    /**
     * Grava (cria ou altera) o de-para de uma conta e o evento. Sem mudança de destino nem de estado, não grava nada.
     *
     * @param manual escolha do Admin: muda até conta já confirmada; as fontes automáticas nunca tocam numa confirmada
     */
    private DeparaConta gravar(Contexto ctx, String conta, String nome, Destino destino, EstadoDepara estado,
            OrigemDepara origem, String motivo, boolean igual, String usuario, Instant agora, boolean manual) {
        Destino novo = comTexto(destino, ctx.porId());
        Optional<DeparaConta> existente = deparas.findByPrevisaoIdAndContaCodigo(ctx.po().getId(), conta);
        if (existente.isEmpty()) {
            DeparaConta d = new DeparaConta(ctx.po(), conta, nome, novo, estado, origem, motivo, igual, usuario, agora);
            deparas.save(d);
            eventos.save(new EventoDepara(d, estado == EstadoDepara.CONFIRMADO ? EventoDepara.Acao.CONFIRMADO
                    : EventoDepara.Acao.SUGERIDO, null, null, novo, usuario, agora));
            return d;
        }
        DeparaConta d = existente.get();
        if (!manual && d.getEstado() == EstadoDepara.CONFIRMADO) {
            return d;
        }
        Destino antes = d.destino(ctx.porId());
        EstadoDepara estadoAntes = d.getEstado();
        boolean mudouDestino = !antes.mesmo(novo);
        if (!mudouDestino && estadoAntes == estado) {
            return d;
        }
        d.atualizarNome(nome);
        d.alterar(novo, estado, origem, motivo, igual, usuario, agora);
        deparas.save(d);
        EventoDepara.Acao acao = mudouDestino ? EventoDepara.Acao.ALTERADO
                : estado == EstadoDepara.CONFIRMADO ? EventoDepara.Acao.CONFIRMADO
                : estado == EstadoDepara.RECUSADO ? EventoDepara.Acao.RECUSADO : EventoDepara.Acao.SUGERIDO;
        eventos.save(new EventoDepara(d, acao, antes, estadoAntes, novo, usuario, agora));
        return d;
    }

    private static Destino comTexto(Destino d, Map<UUID, LinhaPo> porId) {
        LinhaPo l = d.linhaPoId() == null ? null : porId.get(d.linhaPoId());
        return l == null ? d : Destino.linha(l);
    }

    private PrevisaoOrcamentaria po(UUID condominioId, UUID poId) {
        return previsoes.findByIdAndCondominioId(poId, condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO não encontrada"));
    }

    private Contexto contextoParaEscrita(UUID condominioId, UUID poId) {
        PrevisaoOrcamentaria po = po(condominioId, poId);
        if (!po.getEstado().travada()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Confirme a PO antes do de-para: o de-para é por versão confirmada da PO");
        }
        return contexto(po);
    }

    private Contexto contexto(PrevisaoOrcamentaria po) {
        List<LinhaPo> lidas = linhas.findByPrevisaoIdOrderByOrdem(po.getId());
        EstruturaPo estrutura = EstruturaPo.de(lidas);
        Map<UUID, LinhaPo> porId = lidas.stream().collect(Collectors.toMap(LinhaPo::getId, Function.identity()));
        Map<String, LinhaPo> destinos = new LinkedHashMap<>();
        destinosDeDebito(estrutura).forEach(l -> destinos.putIfAbsent(l.getCodigoEfetivo(), l));
        return new Contexto(po, lidas, estrutura, porId, destinos);
    }

    /** Linhas que podem receber débito: linhas dos grupos de despesa (1.1 a 1.8), nunca total, grupo ou fundo. */
    static List<LinhaPo> destinosDeDebito(EstruturaPo estrutura) {
        return estrutura.gruposSemFundos().stream().flatMap(g -> g.linhas().stream()).toList();
    }

    /** Versão confirmada imediatamente anterior desta PO no condomínio (maior versão menor que esta). */
    private Optional<PrevisaoOrcamentaria> versaoAnterior(PrevisaoOrcamentaria po) {
        if (po.getVersao() == null) {
            return Optional.empty();
        }
        return previsoes.findByCondominioIdAndEstadoIn(po.getCondominioId(),
                        EnumSet.of(EstadoPrevisao.CONFIRMADA, EstadoPrevisao.SUBSTITUIDA)).stream()
                .filter(p -> p.getVersao() != null && p.getVersao() < po.getVersao())
                .max(Comparator.comparing(PrevisaoOrcamentaria::getVersao));
    }

    /**
     * Contas com débito no fundo Condomínio (fundo ordinário confirmado) no exercício da PO e nos meses prorrogados,
     * pelo código.
     */
    Map<String, ContaDoFluxo> contasDoFluxo(PrevisaoOrcamentaria po) {
        if (po.getExercicioInicio() == null) {
            return Map.of();
        }
        UUID ordinario = condominios.findById(po.getCondominioId()).map(Condominium::getOperatingFundId).orElse(null);
        if (ordinario == null) {
            return Map.of();
        }
        LocalDate inicio = po.getExercicioInicio().atDay(1);
        // Os meses prorrogados usam o de-para desta PO (RF-11.3)
        LocalDate fim = Optional.ofNullable(po.getProrrogadaAte()).orElse(po.getExercicioFim()).atEndOfMonth();
        return agrupar(lancamentos.debitsWithAccount(po.getCondominioId(), ordinario, inicio, fim));
    }

    /** Agrupa pelo código; o nome é o do lançamento mais recente (a lista vem em ordem de data). */
    static Map<String, ContaDoFluxo> agrupar(List<LedgerEntry> debitos) {
        Map<String, ContaDoFluxo> porCodigo = new TreeMap<>();
        for (LedgerEntry l : debitos) {
            porCodigo.merge(l.getAccountCode(), new ContaDoFluxo(l.getAccountCode(), l.getAccountName(), 1, l.getDebit()),
                    (a, b) -> new ContaDoFluxo(a.codigo(), b.nome() == null || b.nome().isBlank() ? a.nome() : b.nome(),
                            a.lancamentos() + 1, a.debitos().add(b.debitos())));
        }
        return porCodigo;
    }

    private static ContaDepara conta(ContaDoFluxo c, DeparaConta d, Contexto ctx) {
        if (d == null) {
            return new ContaDepara(c.codigo(), c.nome(), c.lancamentos(), c.debitos(), null, null, null, null, false,
                    null, null);
        }
        return new ContaDepara(c.codigo(), c.nome() != null ? c.nome() : d.getContaNome(), c.lancamentos(), c.debitos(),
                DestinoDto.de(d.destino(ctx.porId())), d.getEstado(), d.getOrigem(), d.getMotivo(),
                d.isIgualVersaoAnterior(), d.getAtualizadoPor(), d.getAtualizadoEm());
    }

    private static boolean passa(ContaDepara c, FiltroDepara f) {
        return switch (f) {
            case TODAS -> true;
            case PENDENTES -> c.estado() != EstadoDepara.CONFIRMADO;
            case SUGERIDO -> c.estado() == EstadoDepara.SUGERIDO;
            case CONFIRMADO -> c.estado() == EstadoDepara.CONFIRMADO;
            case RECUSADO -> c.estado() == EstadoDepara.RECUSADO;
            case SEM_DEPARA -> c.estado() == null;
            case IGUAIS_VERSAO_ANTERIOR -> c.igualVersaoAnterior() && c.estado() != null;
        };
    }

    private static int contar(List<ContaDepara> contas, EstadoDepara estado) {
        return (int) contas.stream().filter(c -> c.estado() == estado).count();
    }

    private static Map<String, DeparaConta> porConta(List<DeparaConta> lista) {
        Map<String, DeparaConta> m = new LinkedHashMap<>();
        lista.forEach(d -> m.put(d.getContaCodigo(), d));
        return m;
    }

    private static String validarConta(String conta) {
        String c = conta == null ? "" : conta.trim();
        if (!CONTA.matcher(c).matches()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Conta do fluxo \"" + conta + "\" não é um código numérico");
        }
        return c;
    }
}
