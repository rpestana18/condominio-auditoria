package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import br.com.condominioauditoria.backend.arquivo.ArquivoRepository;
import br.com.condominioauditoria.backend.arquivo.Categoria;
import br.com.condominioauditoria.backend.condominio.Condominio;
import br.com.condominioauditoria.backend.condominio.CondominioRepository;
import br.com.condominioauditoria.backend.contabil.ImpressaoLancamento;
import br.com.condominioauditoria.backend.contabil.Lancamento;
import br.com.condominioauditoria.backend.contabil.LancamentoRepository;
import br.com.condominioauditoria.backend.orcamento.RealocacaoDtos.PedidoRealocacao;
import br.com.condominioauditoria.backend.orcamento.RealocacaoDtos.RealocacaoDto;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
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
 * Realocação mínima (RF-03.1.7; ADR 0004, Decisão 3): o Gestor ou o Admin leva um lançamento "a realocar" do fundo
 * Condomínio a uma linha de despesa da PO que vale no mês, e pode desfazer. Sem IA e sem rubrica nova (RF-02B.3 e
 * RF-02B.5 ficam fora). O lançamento original não muda; cada ação grava um evento na trilha e dispara o recálculo dos
 * achados depois do commit.
 */
@Service
public class ServicoRealocacao {

    private static final Logger log = LoggerFactory.getLogger(ServicoRealocacao.class);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CondominioRepository condominios;
    private final LancamentoRepository lancamentos;
    private final ArquivoRepository arquivos;
    private final ConsultaPrevisao consultaPrevisao;
    private final PrevisaoOrcamentariaRepository previsoes;
    private final LinhaPoRepository linhas;
    private final DeparaContaRepository deparas;
    private final RealocacaoLancamentoRepository realocacoes;
    private final EventoRealocacaoRepository eventos;
    private final ApplicationEventPublisher publicador;

    ServicoRealocacao(CondominioRepository condominios, LancamentoRepository lancamentos, ArquivoRepository arquivos,
            ConsultaPrevisao consultaPrevisao, PrevisaoOrcamentariaRepository previsoes, LinhaPoRepository linhas,
            DeparaContaRepository deparas, RealocacaoLancamentoRepository realocacoes,
            EventoRealocacaoRepository eventos, ApplicationEventPublisher publicador) {
        this.condominios = condominios;
        this.lancamentos = lancamentos;
        this.arquivos = arquivos;
        this.consultaPrevisao = consultaPrevisao;
        this.previsoes = previsoes;
        this.linhas = linhas;
        this.deparas = deparas;
        this.realocacoes = realocacoes;
        this.eventos = eventos;
        this.publicador = publicador;
    }

    @Transactional
    public RealocacaoDto realocar(UUID condominioId, PedidoRealocacao pedido, String usuario) {
        if (pedido == null || pedido.lancamentoId() == null || pedido.linhaId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Informe o lançamento e a linha da PO");
        }
        Condominio condominio = condominios.findById(condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        Lancamento l = lancamentos.findById(pedido.lancamentoId())
                .filter(x -> x.getCondominioId().equals(condominioId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lançamento não encontrado (se o fluxo foi reprocessado, abra o mês de novo)"));
        Arquivo arquivo = arquivos.findById(l.getArquivoId())
                .filter(a -> a.getCategoria() == Categoria.BALANCETE
                        && ConsultaPrevistoRealizado.FLUXO_LIDO.contains(a.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "O lançamento não é de um fluxo de caixa lido"));
        if (!l.getFundoId().equals(condominio.getFundoOrdinarioId()) || l.getDebito().signum() == 0
                || l.isTransferenciaEntreFundos()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Só débitos do fundo Condomínio (fundo ordinário) são realocados");
        }
        YearMonth mes = YearMonth.from(l.getData());
        PrevisaoOrcamentaria po = consultaPrevisao.vigenteNoMes(condominioId, mes)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Sem PO aprovada para "
                        + CalculoPrevistoRealizado.mmaaaa(mes)));
        DeparaConta depara = l.getContaCodigo() == null ? null
                : deparas.findByPrevisaoIdAndContaCodigo(po.getId(), l.getContaCodigo()).orElse(null);
        if (depara == null || depara.getEstado() != EstadoDepara.CONFIRMADO
                || depara.getTipoDestino() != TipoDestino.A_REALOCAR) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Só lançamentos \"a realocar\" são"
                    + " realocados: a conta " + l.getContaCodigo() + " não tem de-para confirmado para REALOCAR");
        }
        List<LinhaPo> lidas = linhas.findByPrevisaoIdOrderByOrdem(po.getId());
        LinhaPo destino = ServicoDepara.destinosDeDebito(EstruturaPo.de(lidas)).stream()
                .filter(x -> x.getId().equals(pedido.linhaId())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "A linha informada não é uma linha de despesa (1.1 a 1.8) da PO que vale em "
                                + CalculoPrevistoRealizado.mmaaaa(mes)));
        realocacoes.findByPrevisaoIdAndChaveLancamentoAndDesfeitaEmIsNull(po.getId(), ImpressaoLancamento.chave(l))
                .ifPresent(r -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Lançamento já realocado em " + r.getRealocadaEm() + ": desfaça antes de realocar de novo");
                });
        Instant agora = Instant.now();
        RealocacaoLancamento r = realocacoes.save(new RealocacaoLancamento(po, l, arquivo.getSha256(), destino, usuario,
                agora));
        String descricao = "realocação do lançamento de " + DATA.format(l.getData()) + " (conta " + l.getContaCodigo()
                + ", R$ " + DinheiroBr.formatar(l.getDebito()) + ") para " + destino.getCodigoEfetivo() + " "
                + destino.getDescricao();
        eventos.save(new EventoRealocacao(r, EventoRealocacao.REALOCADA, usuario, agora, descricao + "; arquivo "
                + arquivo.getNomeOriginal() + ", página " + l.getPagina() + ", ordem " + l.getOrdem()));
        publicador.publishEvent(MudancaOrcamento.de(condominioId, descricao, usuario, agora));
        log.info("Realocação {}: lançamento {} → linha {} por {}", r.getId(), l.getId(), destino.getCodigoEfetivo(),
                usuario);
        return RealocacaoDto.de(r, destino);
    }

    /** Desfazer devolve o valor a "a realocar"; a realocação fica encerrada, com o evento, e nunca é apagada. */
    @Transactional
    public RealocacaoDto desfazer(UUID condominioId, UUID realocacaoId, String usuario) {
        RealocacaoLancamento r = realocacoes.findByIdAndCondominioId(realocacaoId, condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Realocação não encontrada"));
        if (!r.ativa()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Realocação já desfeita em " + r.getDesfeitaEm());
        }
        Instant agora = Instant.now();
        r.desfazer(usuario, agora);
        realocacoes.save(r);
        LinhaPo linha = linhas.findByPrevisaoIdOrderByOrdem(r.getPrevisaoId()).stream()
                .filter(x -> x.getId().equals(r.getLinhaPoId())).findFirst().orElse(null);
        String descricao = "realocação do lançamento de " + DATA.format(r.getData()) + " (conta " + r.getContaCodigo()
                + ", R$ " + DinheiroBr.formatar(r.getValor()) + ") desfeita";
        eventos.save(new EventoRealocacao(r, EventoRealocacao.DESFEITA, usuario, agora, descricao
                + (linha == null ? "" : "; estava em " + linha.getCodigoEfetivo() + " " + linha.getDescricao())));
        publicador.publishEvent(MudancaOrcamento.de(condominioId, descricao, usuario, agora));
        log.info("Realocação {} desfeita por {}", r.getId(), usuario);
        return RealocacaoDto.de(r, linha);
    }

    /** Realocações da versão da PO, ativas e desfeitas (a trilha mostra as duas). */
    @Transactional(readOnly = true)
    public List<RealocacaoDto> listar(UUID condominioId, UUID poId) {
        PrevisaoOrcamentaria po = previsoes.findByIdAndCondominioId(poId, condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO não encontrada"));
        Map<UUID, LinhaPo> porId = linhas.findByPrevisaoIdOrderByOrdem(po.getId()).stream()
                .collect(Collectors.toMap(LinhaPo::getId, Function.identity()));
        return realocacoes.findByPrevisaoIdOrderByDataAscRealocadaEmAsc(po.getId()).stream()
                .map(r -> RealocacaoDto.de(r, porId.get(r.getLinhaPoId()))).toList();
    }
}
