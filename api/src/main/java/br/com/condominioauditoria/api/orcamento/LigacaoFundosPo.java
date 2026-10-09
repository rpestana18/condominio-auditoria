package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.condominio.Condominio;
import br.com.condominioauditoria.api.condominio.CondominioRepository;
import br.com.condominioauditoria.api.contabil.Fundo;
import br.com.condominioauditoria.api.contabil.FundoRepository;
import br.com.condominioauditoria.api.orcamento.PedidoConfirmacao.LigacaoFundo;
import br.com.condominioauditoria.api.orcamento.PrevisaoDtos.PrevisaoDetalhe;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Alteração da ligação das linhas 1.9.x aos fundos do fluxo depois da confirmação da PO (RF-03.1.9 e RF-03.1.13):
 * só o Admin; cada linha com no máximo um fundo e cada fundo em no máximo uma linha; o fundo ordinário nunca. A lista
 * enviada é a ligação completa: linha ausente ou com fundo nulo fica sem fundo ("linha 1.9.x sem fundo ligado"). A
 * trilha (evento FUNDOS_ALTERADOS) registra o fundo anterior e o novo de cada linha, com quem e quando, e os achados
 * são recalculados depois do commit.
 */
@Service
public class LigacaoFundosPo {

    private static final Logger log = LoggerFactory.getLogger(LigacaoFundosPo.class);
    static final String SEM_FUNDO = "(sem fundo)";

    /** Ligação completa das linhas de fundo da PO. */
    public record PedidoFundos(List<LigacaoFundo> fundos) {
    }

    private final CondominioRepository condominios;
    private final PrevisaoOrcamentariaRepository previsoes;
    private final LinhaPoRepository linhas;
    private final PoFundoRepository poFundos;
    private final FundoRepository fundos;
    private final EventoPrevisaoRepository eventos;
    private final ConsultaPrevisao consulta;
    private final ApplicationEventPublisher publicador;

    LigacaoFundosPo(CondominioRepository condominios, PrevisaoOrcamentariaRepository previsoes,
            LinhaPoRepository linhas, PoFundoRepository poFundos, FundoRepository fundos,
            EventoPrevisaoRepository eventos, ConsultaPrevisao consulta, ApplicationEventPublisher publicador) {
        this.condominios = condominios;
        this.previsoes = previsoes;
        this.linhas = linhas;
        this.poFundos = poFundos;
        this.fundos = fundos;
        this.eventos = eventos;
        this.consulta = consulta;
        this.publicador = publicador;
    }

    @Transactional
    public PrevisaoDetalhe alterar(UUID condominioId, UUID poId, PedidoFundos pedido, String usuario) {
        Condominio condominio = condominios.travar(condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        PrevisaoOrcamentaria po = previsoes.findByIdAndCondominioId(poId, condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO não encontrada"));
        if (!po.getEstado().travada()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A PO ainda não foi confirmada: ligue os fundos na confirmação");
        }
        List<LigacaoFundo> pedidos = pedido == null || pedido.fundos() == null ? List.of() : pedido.fundos();
        List<LinhaPo> deFundo = EstruturaPo.de(linhas.findByPrevisaoIdOrderByOrdem(po.getId())).fundos()
                .map(EstruturaPo.Grupo::linhas).orElse(List.of());
        Map<UUID, LinhaPo> porId = deFundo.stream().collect(Collectors.toMap(LinhaPo::getId, Function.identity()));
        Map<UUID, Fundo> doCondominio = fundos.findByCondominioId(condominioId).stream()
                .collect(Collectors.toMap(Fundo::getId, Function.identity()));

        List<String> motivos = new ArrayList<>();
        Map<UUID, UUID> novo = new LinkedHashMap<>();
        Set<UUID> linhasVistas = new HashSet<>();
        Set<UUID> fundosUsados = new HashSet<>();
        for (LigacaoFundo f : pedidos) {
            LinhaPo linha = f.linhaId() == null ? null : porId.get(f.linhaId());
            if (linha == null) {
                motivos.add("Ligação de fundo para uma linha que não é linha de fundo desta PO.");
                continue;
            }
            if (!linhasVistas.add(linha.getId())) {
                motivos.add("Mais de um fundo para a linha " + linha.getCodigoEfetivo() + ".");
                continue;
            }
            if (f.fundoId() == null) {
                continue;
            }
            Fundo fundo = doCondominio.get(f.fundoId());
            if (fundo == null) {
                motivos.add("O fundo informado para a linha " + linha.getCodigoEfetivo() + " não é deste condomínio.");
            } else if (fundo.getId().equals(condominio.getFundoOrdinarioId())) {
                motivos.add("O fundo \"" + fundo.getNome() + "\" é o fundo ordinário e não pode ser ligado à linha "
                        + linha.getCodigoEfetivo() + ".");
            } else if (!fundosUsados.add(fundo.getId())) {
                motivos.add("O fundo \"" + fundo.getNome() + "\" foi ligado a mais de uma linha de fundo.");
            } else {
                novo.put(linha.getId(), fundo.getId());
            }
        }
        if (!motivos.isEmpty()) {
            throw new ConfirmacaoRecusadaException(HttpStatus.UNPROCESSABLE_CONTENT, motivos);
        }

        Map<UUID, UUID> anterior = new HashMap<>();
        poFundos.findByPrevisaoId(po.getId()).forEach(p -> anterior.put(p.getLinhaPoId(), p.getFundoId()));
        List<String> trocas = new ArrayList<>();
        for (LinhaPo l : deFundo) {
            UUID antes = anterior.get(l.getId());
            UUID depois = novo.get(l.getId());
            if (!Objects.equals(antes, depois)) {
                trocas.add(l.getCodigoEfetivo() + " " + l.getDescricao() + ": " + nome(doCondominio, antes) + " → "
                        + nome(doCondominio, depois));
            }
        }
        if (trocas.isEmpty()) {
            return consulta.detalhe(po);
        }
        Instant agora = Instant.now();
        poFundos.apagarDaPrevisao(po.getId());
        novo.forEach((linha, fundo) -> poFundos.save(new PoFundo(po.getId(), linha, fundo)));
        eventos.save(new EventoPrevisao(po, EventoPrevisao.FUNDOS_ALTERADOS, usuario, agora, null,
                "Ligação dos fundos alterada: " + String.join("; ", trocas)));
        publicador.publishEvent(MudancaOrcamento.de(condominioId, "ligação dos fundos da PO versão " + po.getVersao()
                + " alterada (" + String.join("; ", trocas) + ")", usuario, agora));
        log.info("PO {}: ligação dos fundos alterada por {}: {}", po.getId(), usuario, trocas);
        return consulta.detalhe(po);
    }

    private static String nome(Map<UUID, Fundo> fundos, UUID id) {
        if (id == null) {
            return SEM_FUNDO;
        }
        Fundo f = fundos.get(id);
        return f == null ? "fundo " + id : f.getNome();
    }
}
