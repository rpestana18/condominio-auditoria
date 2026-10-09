package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.AcaoLoteRubrica;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.EventoRubricaDto;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.FiltroRubrica;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.LinhaComRubrica;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.LinhaIgnorada;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.LinhaSemSugestao;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.PedidoLoteRubrica;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.PedidoNovaRubrica;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.PedidoRenomear;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.PedidoRubricaLinha;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.ResultadoLoteRubrica;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.ResultadoSugestoesRubrica;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.ResumoRubricas;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.RubricaDto;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.RubricasDaPo;
import br.com.condominioauditoria.api.orcamento.SugestaoRubrica.Confirmada;
import br.com.condominioauditoria.api.orcamento.SugestaoRubrica.LinhaComGrupo;
import br.com.condominioauditoria.api.repository.budget.BudgetLineRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Catálogo de rubricas do condomínio e rubrica de cada linha das POs confirmadas (RF-11.7; ADR 0005, Decisão 1).
 * A primeira PO confirmada gera uma rubrica por linha, já confirmada. Nas outras, a sugestão ({@link SugestaoRubrica})
 * entra sempre como SUGERIDO e só vale na comparação depois que o Admin confirma. Toda mudança grava um evento na
 * trilha (só de inserção). Rubrica não muda o previsto × realizado de nenhum exercício: não dispara recálculo.
 */
@Service
public class ServicoRubricas {

    private static final Logger log = LoggerFactory.getLogger(ServicoRubricas.class);
    private static final int TAMANHO_NOME = 300;

    private final CondominiumRepository condominios;
    private final BudgetRepository previsoes;
    private final BudgetLineRepository linhas;
    private final RubricaRepository rubricas;
    private final LinhaRubricaRepository linhasRubrica;
    private final EventoRubricaRepository eventos;

    ServicoRubricas(CondominiumRepository condominios, BudgetRepository previsoes, BudgetLineRepository linhas,
            RubricaRepository rubricas, LinhaRubricaRepository linhasRubrica, EventoRubricaRepository eventos) {
        this.condominios = condominios;
        this.previsoes = previsoes;
        this.linhas = linhas;
        this.rubricas = rubricas;
        this.linhasRubrica = linhasRubrica;
        this.eventos = eventos;
    }

    /** PO, linhas que recebem rubrica e as rubricas já ligadas, carregadas uma vez por pedido. */
    private record Contexto(Budget po, List<LinhaComGrupo> linhas, Map<UUID, LinhaComGrupo> porId,
            Map<UUID, LinhaRubrica> ligadas) {
    }

    @Transactional(readOnly = true)
    public List<RubricaDto> catalogo(UUID condominioId) {
        return rubricas.findByCondominioIdOrderByNome(condominioId).stream().map(RubricaDto::de).toList();
    }

    @Transactional
    public RubricaDto criar(UUID condominioId, PedidoNovaRubrica pedido, String usuario) {
        String nome = validarNome(pedido == null ? null : pedido.nome());
        String grupo = pedido.grupo() == null || pedido.grupo().isBlank() ? null : pedido.grupo().trim();
        Rubrica r = new Rubrica(condominioId, nome, grupo, null, usuario, Instant.now());
        rubricas.save(r);
        eventos.save(EventoRubrica.criada(r, null, usuario, r.getCriadaEm()));
        log.info("Rubrica {} \"{}\" criada no condomínio {} por {}", r.getId(), nome, condominioId, usuario);
        return RubricaDto.de(r);
    }

    @Transactional
    public RubricaDto renomear(UUID condominioId, UUID rubricaId, PedidoRenomear pedido, String usuario) {
        Rubrica r = rubrica(condominioId, rubricaId);
        String nome = validarNome(pedido == null ? null : pedido.nome());
        if (!nome.equals(r.getNome())) {
            String antes = r.getNome();
            r.renomear(nome);
            rubricas.save(r);
            eventos.save(EventoRubrica.renomeada(r, antes, usuario, Instant.now()));
        }
        return RubricaDto.de(r);
    }

    @Transactional(readOnly = true)
    public RubricasDaPo listar(UUID condominioId, UUID poId, FiltroRubrica filtro) {
        Contexto ctx = contexto(po(condominioId, poId));
        Map<UUID, Rubrica> catalogo = rubricas.findByCondominioIdOrderByNome(condominioId).stream()
                .collect(Collectors.toMap(Rubrica::getId, Function.identity()));
        List<LinhaComRubrica> todas = ctx.linhas().stream()
                .map(l -> linhaComRubrica(l, ctx.ligadas().get(l.linha().getId()), catalogo)).toList();
        ResumoRubricas resumo = new ResumoRubricas(todas.size(), contar(todas, EstadoRubrica.CONFIRMADO),
                contar(todas, EstadoRubrica.SUGERIDO), contar(todas, EstadoRubrica.RECUSADO),
                (int) todas.stream().filter(l -> l.estado() == null).count());
        FiltroRubrica f = filtro == null ? FiltroRubrica.TODAS : filtro;
        return new RubricasDaPo(ctx.po().getId(), ctx.po().getVersion(), resumo,
                todas.stream().filter(l -> passa(l, f)).toList());
    }

    /**
     * Gera as rubricas da PO: na primeira PO confirmada do condomínio, uma rubrica por linha, já confirmada; nas
     * outras, sugestões para as linhas ainda sem rubrica. Nunca mexe em linha que já tem rubrica.
     */
    @Transactional
    public ResultadoSugestoesRubrica sugerir(UUID condominioId, UUID poId, String usuario) {
        condominios.lockById(condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        return gerar(contextoParaEscrita(condominioId, poId).po(), usuario, Instant.now());
    }

    /**
     * Chamado na confirmação da PO, na mesma transação (o condomínio já está travado). A PO precisa estar confirmada.
     */
    public ResultadoSugestoesRubrica aoConfirmar(Budget po, String usuario, Instant agora) {
        return gerar(po, usuario, agora);
    }

    private ResultadoSugestoesRubrica gerar(Budget po, String usuario, Instant agora) {
        Contexto ctx = contexto(po);
        if (!rubricas.existsByCondominioId(po.getCondominiumId())) {
            for (LinhaComGrupo l : ctx.linhas()) {
                Rubrica r = new Rubrica(po.getCondominiumId(), nomeDe(l), l.grupo(), l.linha().getId(), usuario, agora);
                rubricas.save(r);
                ligar(l.linha(), null, r, EstadoRubrica.CONFIRMADO, OrigemRubrica.PRIMEIRA_PO,
                        "primeira PO confirmada do condomínio: a linha vira rubrica", usuario, agora);
            }
            log.info("Rubricas: primeira PO {} do condomínio {}, {} rubricas criadas", po.getId(), po.getCondominiumId(),
                    ctx.linhas().size());
            return new ResultadoSugestoesRubrica(true, ctx.linhas().size(), 0, 0, 0, List.of());
        }

        List<LinhaComGrupo> novas = ctx.linhas().stream().filter(l -> !ctx.ligadas().containsKey(l.linha().getId()))
                .toList();
        Optional<Budget> anterior = versaoAnterior(po);
        List<Confirmada> daAnterior = new ArrayList<>();
        List<Confirmada> deOutras = new ArrayList<>();
        Map<UUID, List<LinhaRubrica>> confirmadasPorPo = linhasRubrica
                .findByCondominioIdAndEstado(po.getCondominiumId(), EstadoRubrica.CONFIRMADO).stream()
                .filter(lr -> !lr.getPrevisaoId().equals(po.getId()))
                .collect(Collectors.groupingBy(LinhaRubrica::getPrevisaoId, LinkedHashMap::new, Collectors.toList()));
        confirmadasPorPo.forEach((previsaoId, ligadas) -> {
            Map<UUID, LinhaComGrupo> daPo = SugestaoRubrica.linhas(BudgetStructure.of(
                    linhas.findByBudgetIdOrderByPosition(previsaoId))).stream()
                    .collect(Collectors.toMap(l -> l.linha().getId(), Function.identity()));
            for (LinhaRubrica lr : ligadas) {
                LinhaComGrupo l = daPo.get(lr.getLinhaPoId());
                if (l != null) {
                    Confirmada c = new Confirmada(l, lr.getRubricaId());
                    deOutras.add(c);
                    if (anterior.isPresent() && anterior.get().getId().equals(previsaoId)) {
                        daAnterior.add(c);
                    }
                }
            }
        });

        Map<UUID, Rubrica> catalogo = rubricas.findByCondominioIdOrderByNome(po.getCondominiumId()).stream()
                .collect(Collectors.toMap(Rubrica::getId, Function.identity()));
        int versaoAnterior = 0;
        int pelaConta = 0;
        List<LinhaSemSugestao> semSugestao = new ArrayList<>();
        var resultados = SugestaoRubrica.sugerir(novas, ctx.linhas(), daAnterior, deOutras);
        for (LinhaComGrupo l : novas) {
            var r = resultados.get(l.linha().getId());
            if (r instanceof SugestaoRubrica.Sugerida s && catalogo.containsKey(s.rubricaId())) {
                ligar(l.linha(), null, catalogo.get(s.rubricaId()), EstadoRubrica.SUGERIDO, s.origem(), s.motivo(),
                        usuario, agora);
                if (s.origem() == OrigemRubrica.VERSAO_ANTERIOR) {
                    versaoAnterior++;
                } else {
                    pelaConta++;
                }
            } else {
                semSugestao.add(new LinhaSemSugestao(l.linha().getId(), l.linha().getEffectiveCode(), l.rotulo(),
                        r.motivo()));
            }
        }
        log.info("Rubricas da PO {}: {} da versão anterior, {} pela conta, {} sem sugestão", po.getId(), versaoAnterior,
                pelaConta, semSugestao.size());
        return new ResultadoSugestoesRubrica(false, 0, versaoAnterior + pelaConta, versaoAnterior, pelaConta,
                semSugestao);
    }

    /** Admin escolhe a rubrica de uma linha: uma do catálogo ou uma nova criada a partir da linha. */
    @Transactional
    public LinhaComRubrica definir(UUID condominioId, UUID poId, UUID linhaId, PedidoRubricaLinha pedido,
            String usuario) {
        Contexto ctx = contextoParaEscrita(condominioId, poId);
        LinhaComGrupo l = ctx.porId().get(linhaId);
        if (l == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "A linha informada não é uma linha de despesa ou de fundo desta PO");
        }
        boolean existente = pedido != null && pedido.rubricaId() != null;
        boolean nova = pedido != null && pedido.novaRubrica() != null && !pedido.novaRubrica().isBlank();
        if (existente == nova) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Informe a rubrica do catálogo ou o nome de uma rubrica nova, não os dois");
        }
        Instant agora = Instant.now();
        Rubrica r;
        if (nova) {
            r = new Rubrica(condominioId, validarNome(pedido.novaRubrica()), l.grupo(), l.linha().getId(), usuario,
                    agora);
            rubricas.save(r);
            eventos.save(EventoRubrica.criada(r, l.linha(), usuario, agora));
        } else {
            r = rubrica(condominioId, pedido.rubricaId());
        }
        EstadoRubrica estado = pedido.confirmar() == null || pedido.confirmar() ? EstadoRubrica.CONFIRMADO
                : EstadoRubrica.SUGERIDO;
        LinhaRubrica lr = ligar(l.linha(), ctx.ligadas().get(linhaId), r, estado, OrigemRubrica.MANUAL,
                "escolhida pelo Admin", usuario, agora);
        return linhaComRubrica(l, lr, Map.of(r.getId(), r));
    }

    /** Confirmar ou recusar em lote, um evento por linha alterada. */
    @Transactional
    public ResultadoLoteRubrica lote(UUID condominioId, UUID poId, PedidoLoteRubrica pedido, String usuario) {
        Contexto ctx = contextoParaEscrita(condominioId, poId);
        if (pedido == null || pedido.acao() == null || pedido.linhas() == null || pedido.linhas().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Informe a ação e as linhas");
        }
        EstadoRubrica novo = pedido.acao() == AcaoLoteRubrica.CONFIRMAR ? EstadoRubrica.CONFIRMADO
                : EstadoRubrica.RECUSADO;
        Map<UUID, Rubrica> catalogo = rubricas.findByCondominioIdOrderByNome(condominioId).stream()
                .collect(Collectors.toMap(Rubrica::getId, Function.identity()));
        Instant agora = Instant.now();
        int alteradas = 0;
        List<LinhaIgnorada> ignoradas = new ArrayList<>();
        for (UUID linhaId : pedido.linhas().stream().distinct().toList()) {
            LinhaComGrupo l = ctx.porId().get(linhaId);
            LinhaRubrica lr = ctx.ligadas().get(linhaId);
            if (l == null) {
                ignoradas.add(new LinhaIgnorada(linhaId, "não é uma linha de despesa ou de fundo desta PO"));
            } else if (lr == null) {
                ignoradas.add(new LinhaIgnorada(linhaId, "linha sem rubrica: escolha a rubrica"));
            } else if (lr.getEstado() == novo) {
                ignoradas.add(new LinhaIgnorada(linhaId, "já está " + novo.name().toLowerCase()));
            } else {
                Rubrica r = catalogo.get(lr.getRubricaId());
                EstadoRubrica antes = lr.getEstado();
                lr.mudarEstado(novo, usuario, agora);
                linhasRubrica.save(lr);
                eventos.save(EventoRubrica.daLinha(l.linha(), lr, acaoDoEstado(novo), r, antes, r, usuario, agora));
                alteradas++;
            }
        }
        log.info("Rubricas da PO {}: {} linha(s) {} por {}", ctx.po().getId(), alteradas, novo, usuario);
        return new ResultadoLoteRubrica(alteradas, ignoradas);
    }

    @Transactional(readOnly = true)
    public List<EventoRubricaDto> eventos(UUID condominioId, UUID poId) {
        return eventos.findByPrevisaoIdOrderByEmAscLinhaCodigoAsc(po(condominioId, poId).getId()).stream()
                .map(EventoRubricaDto::de).toList();
    }

    /**
     * Liga (ou troca) a rubrica de uma linha e grava o evento. Sem mudança de rubrica nem de estado, não grava nada.
     */
    private LinhaRubrica ligar(BudgetLine linha, LinhaRubrica atual, Rubrica rubrica, EstadoRubrica estado,
            OrigemRubrica origem, String motivo, String usuario, Instant agora) {
        if (atual == null) {
            LinhaRubrica lr = new LinhaRubrica(linha, rubrica, estado, origem, motivo, usuario, agora);
            linhasRubrica.save(lr);
            eventos.save(EventoRubrica.daLinha(linha, lr, acaoDoEstado(estado), null, null, rubrica, usuario, agora));
            return lr;
        }
        boolean mudouRubrica = !atual.getRubricaId().equals(rubrica.getId());
        if (!mudouRubrica && atual.getEstado() == estado) {
            return atual;
        }
        Rubrica antes = rubricas.findById(atual.getRubricaId()).orElse(null);
        EstadoRubrica estadoAntes = atual.getEstado();
        atual.alterar(rubrica, estado, origem, motivo, usuario, agora);
        linhasRubrica.save(atual);
        eventos.save(EventoRubrica.daLinha(linha, atual, mudouRubrica ? EventoRubrica.Acao.ALTERADO : acaoDoEstado(estado),
                antes, estadoAntes, rubrica, usuario, agora));
        return atual;
    }

    private static EventoRubrica.Acao acaoDoEstado(EstadoRubrica estado) {
        return switch (estado) {
            case SUGERIDO -> EventoRubrica.Acao.SUGERIDO;
            case CONFIRMADO -> EventoRubrica.Acao.CONFIRMADO;
            case RECUSADO -> EventoRubrica.Acao.RECUSADO;
        };
    }

    /** Nome da rubrica criada a partir de uma linha: a conta da PO (ou o texto da conta), senão a descrição. */
    private static String nomeDe(LinhaComGrupo l) {
        String nome = l.rotulo().isBlank() ? l.linha().getEffectiveCode() : l.rotulo();
        return nome.length() > TAMANHO_NOME ? nome.substring(0, TAMANHO_NOME) : nome;
    }

    private Budget po(UUID condominioId, UUID poId) {
        return previsoes.findByIdAndCondominiumId(poId, condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO não encontrada"));
    }

    private Rubrica rubrica(UUID condominioId, UUID rubricaId) {
        return rubricas.findByIdAndCondominioId(rubricaId, condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "A rubrica informada não é deste condomínio"));
    }

    private Contexto contextoParaEscrita(UUID condominioId, UUID poId) {
        Budget po = po(condominioId, poId);
        if (!po.getStatus().isLocked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Confirme a PO antes das rubricas: as rubricas são das linhas de PO confirmada");
        }
        return contexto(po);
    }

    private Contexto contexto(Budget po) {
        List<LinhaComGrupo> daPo = SugestaoRubrica.linhas(BudgetStructure.of(linhas.findByBudgetIdOrderByPosition(po.getId())));
        Map<UUID, LinhaComGrupo> porId = new LinkedHashMap<>();
        daPo.forEach(l -> porId.put(l.linha().getId(), l));
        Map<UUID, LinhaRubrica> ligadas = linhasRubrica.findByPrevisaoId(po.getId()).stream()
                .collect(Collectors.toMap(LinhaRubrica::getLinhaPoId, Function.identity()));
        return new Contexto(po, daPo, porId, ligadas);
    }

    /**
     * Versão confirmada imediatamente anterior do mesmo exercício (a que esta PO substituiu numa reaprovação): maior
     * versão menor que esta, com exercício que se sobrepõe ao desta.
     */
    private Optional<Budget> versaoAnterior(Budget po) {
        if (po.getVersion() == null || po.getFiscalYearStart() == null) {
            return Optional.empty();
        }
        return previsoes.findByCondominiumIdAndStatusIn(po.getCondominiumId(),
                        EnumSet.of(BudgetStatus.CONFIRMADA, BudgetStatus.SUBSTITUIDA)).stream()
                .filter(p -> !p.getId().equals(po.getId()) && p.getVersion() != null && p.getVersion() < po.getVersion())
                .filter(p -> p.getFiscalYearStart() != null && !p.getFiscalYearStart().isAfter(po.getFiscalYearEnd())
                        && !p.getFiscalYearEnd().isBefore(po.getFiscalYearStart()))
                .max(Comparator.comparing(Budget::getVersion));
    }

    private static LinhaComRubrica linhaComRubrica(LinhaComGrupo l, LinhaRubrica lr, Map<UUID, Rubrica> catalogo) {
        BudgetLine linha = l.linha();
        if (lr == null) {
            return new LinhaComRubrica(linha.getId(), linha.getEffectiveCode(), l.grupo(), l.rotulo(),
                    linha.getDescription(), linha.getBudgeted(), null, null, null, null, null, null);
        }
        Rubrica r = catalogo.get(lr.getRubricaId());
        return new LinhaComRubrica(linha.getId(), linha.getEffectiveCode(), l.grupo(), l.rotulo(), linha.getDescription(),
                linha.getBudgeted(), r == null ? null : RubricaDto.de(r), lr.getEstado(), lr.getOrigem(), lr.getMotivo(),
                lr.getAtualizadoPor(), lr.getAtualizadoEm());
    }

    private static boolean passa(LinhaComRubrica l, FiltroRubrica f) {
        return switch (f) {
            case TODAS -> true;
            case PENDENTES -> l.estado() != EstadoRubrica.CONFIRMADO;
            case SUGERIDO -> l.estado() == EstadoRubrica.SUGERIDO;
            case CONFIRMADO -> l.estado() == EstadoRubrica.CONFIRMADO;
            case RECUSADO -> l.estado() == EstadoRubrica.RECUSADO;
            case SEM_RUBRICA -> l.estado() == null;
        };
    }

    private static int contar(List<LinhaComRubrica> linhas, EstadoRubrica estado) {
        return (int) linhas.stream().filter(l -> l.estado() == estado).count();
    }

    private static String validarNome(String nome) {
        String n = nome == null ? "" : nome.trim();
        if (n.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Informe o nome da rubrica");
        }
        if (n.length() > TAMANHO_NOME) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "O nome da rubrica passa de " + TAMANHO_NOME + " caracteres");
        }
        return n;
    }
}
