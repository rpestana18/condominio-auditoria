package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.condominio.CondominioRepository;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.PedidoProrrogacao;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.PrevisaoDetalhe;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * PO prorrogada (RF-11.3; ADR 0005, Decisão 4): o Admin marca a PO confirmada como prorrogada até um mês depois do
 * fim do exercício, com justificativa. Nunca é automática. Recusada se algum mês prorrogado já tem PO confirmada ou
 * prorrogação de outra PO. Os meses prorrogados usam esta PO e o seu de-para; o recálculo dos achados roda depois.
 */
@Service
public class ServicoProrrogacao {

    private static final Logger log = LoggerFactory.getLogger(ServicoProrrogacao.class);

    private final CondominioRepository condominios;
    private final PrevisaoOrcamentariaRepository previsoes;
    private final EventoPrevisaoRepository eventos;
    private final ConsultaPrevisao consulta;
    private final ApplicationEventPublisher publicador;

    ServicoProrrogacao(CondominioRepository condominios, PrevisaoOrcamentariaRepository previsoes,
            EventoPrevisaoRepository eventos, ConsultaPrevisao consulta, ApplicationEventPublisher publicador) {
        this.condominios = condominios;
        this.previsoes = previsoes;
        this.eventos = eventos;
        this.consulta = consulta;
        this.publicador = publicador;
    }

    @Transactional
    public PrevisaoDetalhe prorrogar(UUID condominioId, UUID poId, PedidoProrrogacao pedido, String usuario) {
        PrevisaoOrcamentaria po = poParaEscrita(condominioId, poId);
        String justificativa = pedido == null || pedido.justificativa() == null ? "" : pedido.justificativa().trim();
        if (justificativa.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "A prorrogação exige justificativa");
        }
        YearMonth ate = mes(pedido.ate());
        YearMonth primeiro = po.getExercicioFim().plusMonths(1);
        if (ate.isBefore(primeiro)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "A prorrogação termina depois do fim"
                    + " do exercício (" + po.getExercicioFim() + "): informe " + primeiro + " ou depois");
        }
        List<PrevisaoOrcamentaria> outras = previsoes.findByCondominioIdAndEstadoIn(condominioId,
                        EnumSet.of(EstadoPrevisao.CONFIRMADA, EstadoPrevisao.SUBSTITUIDA)).stream()
                .filter(p -> !p.getId().equals(po.getId())).toList();
        for (YearMonth mes : CalculoPrevistoRealizado.meses(primeiro, ate)) {
            Optional<VigenciaPo.PoDoMes> doMes = VigenciaPo.doMes(outras, mes);
            if (doMes.isPresent()) {
                PrevisaoOrcamentaria outra = doMes.get().previsao();
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, CalculoPrevistoRealizado.mmaaaa(mes)
                        + (doMes.get().prorrogada() ? " já está na prorrogação de outra PO" : " já tem PO confirmada")
                        + " (versão " + outra.getVersao() + ", exercício " + outra.getExercicioInicio() + " a "
                        + outra.getExercicioFim() + ")");
            }
        }
        Instant agora = Instant.now();
        String antes = po.getProrrogadaAte() == null ? "sem prorrogação" : "prorrogada até " + po.getProrrogadaAte();
        po.prorrogar(ate, justificativa, usuario, agora);
        previsoes.save(po);
        eventos.save(new EventoPrevisao(po, EventoPrevisao.PRORROGADA, usuario, agora, justificativa,
                "Prorrogada de " + primeiro + " a " + ate + " (antes: " + antes + ")."));
        publicador.publishEvent(MudancaOrcamento.de(condominioId, "PO versão " + po.getVersao() + " prorrogada até "
                + ate, usuario, agora));
        log.info("PO {} prorrogada até {} por {}", po.getId(), ate, usuario);
        return consulta.detalhe(po);
    }

    @Transactional
    public PrevisaoDetalhe desfazer(UUID condominioId, UUID poId, String usuario) {
        PrevisaoOrcamentaria po = poParaEscrita(condominioId, poId);
        YearMonth ate = po.getProrrogadaAte();
        if (ate == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A PO não está prorrogada");
        }
        Instant agora = Instant.now();
        po.desfazerProrrogacao();
        previsoes.save(po);
        eventos.save(new EventoPrevisao(po, EventoPrevisao.PRORROGACAO_DESFEITA, usuario, agora, null,
                "Prorrogação de " + po.getExercicioFim().plusMonths(1) + " a " + ate + " desfeita."));
        publicador.publishEvent(MudancaOrcamento.de(condominioId, "prorrogação da PO versão " + po.getVersao()
                + " desfeita", usuario, agora));
        log.info("Prorrogação da PO {} desfeita por {}", po.getId(), usuario);
        return consulta.detalhe(po);
    }

    private PrevisaoOrcamentaria poParaEscrita(UUID condominioId, UUID poId) {
        // Uma mudança de PO por vez no condomínio, como na confirmação
        condominios.travar(condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        PrevisaoOrcamentaria po = previsoes.findByIdAndCondominioId(poId, condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO não encontrada"));
        if (po.getEstado() != EstadoPrevisao.CONFIRMADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, po.getEstado() == EstadoPrevisao.SUBSTITUIDA
                    ? "PO substituída por outra versão: prorrogue a versão que vale"
                    : "Confirme a PO antes de prorrogar");
        }
        return po;
    }

    private static YearMonth mes(String texto) {
        try {
            return YearMonth.parse(texto == null ? "" : texto.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Informe o último mês prorrogado no formato AAAA-MM: " + texto);
        }
    }
}
