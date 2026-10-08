package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import br.com.condominioauditoria.backend.arquivo.ArquivoRepository;
import br.com.condominioauditoria.backend.auditoria.AchadoRepository;
import br.com.condominioauditoria.backend.contabil.ConferenciaRepository;
import br.com.condominioauditoria.backend.contabil.Fundo;
import br.com.condominioauditoria.backend.contabil.FundoRepository;
import br.com.condominioauditoria.backend.orcamento.AvaliacaoLeituraPo.ConferenciaPo;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.AchadoDto;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.AvisoDto;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.CodigoAviso;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.CodigoRepetidoDto;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.ConferenciaPoDto;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.FundoPoDto;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.LinhaPoDto;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.LinhaRepetidaDto;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.PrevisaoDetalhe;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.PrevisaoResumo;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura da PO para a API. Os avisos são calculados aqui, a partir do que está gravado (ADR 0004, Decisão 3: avisos
 * que não são achado não têm tabela), com a tolerância gravada na própria PO: a consulta sempre repete a gravação.
 */
@Service
public class ConsultaPrevisao {

    private final PrevisaoOrcamentariaRepository previsoes;
    private final LinhaPoRepository linhas;
    private final ConferenciaRepository conferencias;
    private final ArquivoRepository arquivos;
    private final PoFundoRepository poFundos;
    private final FundoRepository fundos;
    private final AchadoRepository achados;
    private final ReservaDaPo reserva;

    ConsultaPrevisao(PrevisaoOrcamentariaRepository previsoes, LinhaPoRepository linhas,
            ConferenciaRepository conferencias, ArquivoRepository arquivos, PoFundoRepository poFundos,
            FundoRepository fundos, AchadoRepository achados, ReservaDaPo reserva) {
        this.previsoes = previsoes;
        this.linhas = linhas;
        this.conferencias = conferencias;
        this.arquivos = arquivos;
        this.poFundos = poFundos;
        this.fundos = fundos;
        this.achados = achados;
        this.reserva = reserva;
    }

    /**
     * A PO que vale no mês (RF-03.1.3), pelo exercício ou por prorrogação (RF-11.3), ou vazio: "sem PO aprovada para
     * este mês".
     */
    @Transactional(readOnly = true)
    public Optional<PrevisaoOrcamentaria> vigenteNoMes(UUID condominioId, YearMonth mes) {
        return VigenciaPo.vigenteNoMes(previsoes.findByCondominioIdAndEstadoIn(condominioId,
                EnumSet.of(EstadoPrevisao.CONFIRMADA, EstadoPrevisao.SUBSTITUIDA)), mes);
    }

    @Transactional(readOnly = true)
    public List<PrevisaoResumo> listar(UUID condominioId) {
        return previsoes.findByCondominioIdOrderByLidaEmDesc(condominioId).stream().map(this::resumo).toList();
    }

    @Transactional(readOnly = true)
    public Optional<PrevisaoDetalhe> detalhe(UUID condominioId, UUID id) {
        return previsoes.findByIdAndCondominioId(id, condominioId).map(this::detalhe);
    }

    PrevisaoDetalhe detalhe(PrevisaoOrcamentaria p) {
        List<LinhaPo> lidas = linhas.findByPrevisaoIdOrderByOrdem(p.getId());
        EstruturaPo estrutura = EstruturaPo.de(lidas);
        var avaliacao = avaliar(p, estrutura);
        Set<UUID> deFundo = new HashSet<>();
        estrutura.fundos().ifPresent(f -> f.linhas().forEach(l -> deFundo.add(l.getId())));

        List<ConferenciaPoDto> conferenciasDto = avaliacao.conferencias().stream()
                .map(a -> new ConferenciaPoDto(a.conferencia().codigo(), a.conferencia().descricao(),
                        a.conferencia().ok(), a.conferencia().detalhe(), a.classificacao(), a.explicacao()))
                .toList();
        List<CodigoRepetidoDto> repetidos = codigosRepetidos(lidas);
        return new PrevisaoDetalhe(resumo(p), p.getColunaOrcadoAnterior(), p.getColunaOrcado(),
                p.getPrevistoMesImpresso(), p.getToleranciaArredondamento(), mes(p.getSubstituidaDesde()),
                p.getEstado().travada() ? new PrevisaoDtos.Confirmacao(p.getAtaArquivoId(), p.isSemAta(),
                        p.getDataAprovacao(), p.isCienteDivergencia(), p.getJustificativaDivergencia()) : null,
                lidas.stream().map(l -> LinhaPoDto.de(l, deFundo.contains(l.getId()))).toList(), conferenciasDto,
                avisos(p, estrutura, avaliacao, repetidos), repetidos, fundos(p, lidas), achados(p));
    }

    private List<FundoPoDto> fundos(PrevisaoOrcamentaria p, List<LinhaPo> lidas) {
        Map<UUID, LinhaPo> porId = lidas.stream().collect(Collectors.toMap(LinhaPo::getId, Function.identity()));
        Map<UUID, String> nomes = fundos.findByCondominioId(p.getCondominioId()).stream()
                .collect(Collectors.toMap(Fundo::getId, Fundo::getNome));
        return poFundos.findByPrevisaoId(p.getId()).stream()
                .map(f -> {
                    LinhaPo l = porId.get(f.getLinhaPoId());
                    return new FundoPoDto(l.getId(), l.getCodigoEfetivo(), l.getDescricao(), l.getOrcado(),
                            f.getFundoId(), nomes.get(f.getFundoId()));
                })
                .sorted(java.util.Comparator.comparing(FundoPoDto::codigoEfetivo))
                .toList();
    }

    private List<AchadoDto> achados(PrevisaoOrcamentaria p) {
        return achados.findByCondominioIdAndAlvoStartingWithOrderByCriadoEm(p.getCondominioId(),
                        ConfirmacaoPrevisao.prefixoAlvo(p)).stream()
                .map(a -> new AchadoDto(a.getId(), a.getRegra(), a.getVersaoRegra(), a.getSeveridade().name(),
                        a.getCompetencia().toString(), a.getDescricao(), a.getEstado().name()))
                .toList();
    }

    AvaliacaoLeituraPo.Resultado avaliar(PrevisaoOrcamentaria p, EstruturaPo estrutura) {
        List<ConferenciaPo> gravadas = conferencias.findByArquivoIdOrderByOrdem(p.getArquivoId()).stream()
                .map(c -> new ConferenciaPo(c.getCodigo(), c.getDescricao(), c.isOk(), c.getDetalhe())).toList();
        return AvaliacaoLeituraPo.avaliar(estrutura, gravadas, p.getToleranciaArredondamento());
    }

    private List<AvisoDto> avisos(PrevisaoOrcamentaria p, EstruturaPo estrutura,
            AvaliacaoLeituraPo.Resultado avaliacao, List<CodigoRepetidoDto> repetidos) {
        List<AvisoDto> avisos = new ArrayList<>();
        if (p.getEstado().travada()) {
            avisos.addAll(avisosDaConfirmacao(p, estrutura, avaliacao));
        }
        avaliacao.arredondamentos().forEach(t -> avisos.add(new AvisoDto(CodigoAviso.ARREDONDAMENTO, t)));
        if (p.getEstado() == EstadoPrevisao.LIDA_COM_DIVERGENCIA) {
            avaliacao.divergencias().forEach(t -> avisos.add(new AvisoDto(CodigoAviso.DIVERGENCIA,
                    "PO lida com divergência: " + t + ". A PO não é usada no previsto × realizado até a confirmação.")));
        }
        repetidos.stream().filter(r -> !r.resolvido()).forEach(r -> avisos.add(new AvisoDto(CodigoAviso.CODIGO_REPETIDO,
                "Código " + r.codigoImpresso() + " impresso " + r.linhas().size()
                        + " vezes: informe um código distinto na confirmação")));
        return avisos;
    }

    /**
     * Avisos informativos da PO confirmada, nunca achados: ciência da divergência (Q29), aprovação fora do 1º
     * trimestre (Conv. 10.2), exercício sem ata e regra que não pôde ser avaliada.
     */
    private List<AvisoDto> avisosDaConfirmacao(PrevisaoOrcamentaria p, EstruturaPo estrutura,
            AvaliacaoLeituraPo.Resultado avaliacao) {
        List<AvisoDto> avisos = new ArrayList<>();
        if (p.isCienteDivergencia()) {
            avisos.add(new AvisoDto(CodigoAviso.CONFIRMADA_COM_DIVERGENCIA,
                    "PO confirmada com divergência: " + String.join("; ", avaliacao.divergencias())));
        }
        if (foraDoPrimeiroTrimestre(p)) {
            avisos.add(new AvisoDto(CodigoAviso.FORA_PRIMEIRO_TRIMESTRE, TEXTO_FORA_PRIMEIRO_TRIMESTRE));
        }
        if (p.isSemAta()) {
            avisos.add(new AvisoDto(CodigoAviso.SEM_ATA, "Exercício informado sem ata: pendência de implantação"));
        }
        if (reserva.avaliar(p, estrutura) instanceof ReservaDaPo.NaoAvaliada n) {
            avisos.add(new AvisoDto(CodigoAviso.REGRA_NAO_AVALIADA, n.motivo()));
        }
        return avisos;
    }

    static final String TEXTO_FORA_PRIMEIRO_TRIMESTRE = "PO aprovada fora do 1º trimestre (Conv. 10.2)";

    /** Mês da aprovação: a data da assembleia; sem ela, o início do exercício (que vem da data da ata). */
    static boolean foraDoPrimeiroTrimestre(PrevisaoOrcamentaria p) {
        int mes = p.getDataAprovacao() != null ? p.getDataAprovacao().getMonthValue()
                : p.getExercicioInicio().getMonthValue();
        return mes > 3;
    }

    /** Códigos impressos mais de uma vez; resolvido quando os códigos efetivos dessas linhas já são distintos. */
    static List<CodigoRepetidoDto> codigosRepetidos(List<LinhaPo> lidas) {
        Map<String, List<LinhaPo>> porCodigo = new LinkedHashMap<>();
        lidas.forEach(l -> porCodigo.computeIfAbsent(l.getCodigoImpresso(), c -> new ArrayList<>()).add(l));
        Map<String, Long> efetivos = new LinkedHashMap<>();
        lidas.forEach(l -> efetivos.merge(l.getCodigoEfetivo(), 1L, Long::sum));
        return porCodigo.entrySet().stream().filter(e -> e.getValue().size() > 1)
                .map(e -> new CodigoRepetidoDto(e.getKey(),
                        e.getValue().stream().allMatch(l -> efetivos.get(l.getCodigoEfetivo()) == 1),
                        e.getValue().stream().map(l -> new LinhaRepetidaDto(l.getId(), l.getOrdem(), l.getDescricao(),
                                l.getCodigoEfetivo())).toList()))
                .toList();
    }

    PrevisaoResumo resumo(PrevisaoOrcamentaria p) {
        String nome = arquivos.findById(p.getArquivoId()).map(Arquivo::getNomeOriginal).orElse(null);
        return new PrevisaoResumo(p.getId(), p.getArquivoId(), nome, p.getSha256(), p.getEstado(), p.getVersao(),
                p.getTitulo(), p.getExercicioImpresso(), mes(p.getExercicioInicio()), mes(p.getExercicioFim()),
                p.getTotalImpresso(), p.getPrevistoMes(), p.getLidaEm(), p.getConfirmadaPor(), p.getConfirmadaEm(),
                PrevisaoDtos.Prorrogacao.de(p));
    }

    static String mes(YearMonth m) {
        return m == null ? null : m.toString();
    }
}
