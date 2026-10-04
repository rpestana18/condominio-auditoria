package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import br.com.condominioauditoria.backend.arquivo.ArquivoRepository;
import br.com.condominioauditoria.backend.contabil.ConferenciaRepository;
import br.com.condominioauditoria.backend.orcamento.AvaliacaoLeituraPo.ConferenciaPo;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.AvisoDto;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.CodigoAviso;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.CodigoRepetidoDto;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.ConferenciaPoDto;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.LinhaPoDto;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.LinhaRepetidaDto;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.PrevisaoDetalhe;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.PrevisaoResumo;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

    ConsultaPrevisao(PrevisaoOrcamentariaRepository previsoes, LinhaPoRepository linhas,
            ConferenciaRepository conferencias, ArquivoRepository arquivos) {
        this.previsoes = previsoes;
        this.linhas = linhas;
        this.conferencias = conferencias;
        this.arquivos = arquivos;
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
                avisos(p, avaliacao, repetidos), repetidos, List.of(), List.of());
    }

    AvaliacaoLeituraPo.Resultado avaliar(PrevisaoOrcamentaria p, EstruturaPo estrutura) {
        List<ConferenciaPo> gravadas = conferencias.findByArquivoIdOrderByOrdem(p.getArquivoId()).stream()
                .map(c -> new ConferenciaPo(c.getCodigo(), c.getDescricao(), c.isOk(), c.getDetalhe())).toList();
        return AvaliacaoLeituraPo.avaliar(estrutura, gravadas, p.getToleranciaArredondamento());
    }

    private List<AvisoDto> avisos(PrevisaoOrcamentaria p, AvaliacaoLeituraPo.Resultado avaliacao,
            List<CodigoRepetidoDto> repetidos) {
        List<AvisoDto> avisos = new ArrayList<>();
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
                p.getTotalImpresso(), p.getPrevistoMes(), p.getLidaEm(), p.getConfirmadaPor(), p.getConfirmadaEm());
    }

    static String mes(YearMonth m) {
        return m == null ? null : m.toString();
    }
}
