package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import br.com.condominioauditoria.backend.arquivo.ArquivoRepository;
import br.com.condominioauditoria.backend.arquivo.Categoria;
import br.com.condominioauditoria.backend.arquivo.StatusArquivo;
import br.com.condominioauditoria.backend.auditoria.ParametroRegra;
import br.com.condominioauditoria.backend.auditoria.ParametroRegraRepository;
import br.com.condominioauditoria.backend.auditoria.RegraExcessoMes;
import br.com.condominioauditoria.backend.condominio.Condominio;
import br.com.condominioauditoria.backend.condominio.CondominioRepository;
import br.com.condominioauditoria.backend.contabil.Fundo;
import br.com.condominioauditoria.backend.contabil.FundoRepository;
import br.com.condominioauditoria.backend.contabil.Lancamento;
import br.com.condominioauditoria.backend.contabil.LancamentoRepository;
import br.com.condominioauditoria.backend.orcamento.CalculoPrevistoRealizado.Acumulado;
import br.com.condominioauditoria.backend.orcamento.CalculoPrevistoRealizado.Calculo;
import br.com.condominioauditoria.backend.orcamento.CalculoPrevistoRealizado.Entrada;
import br.com.condominioauditoria.backend.orcamento.CalculoPrevistoRealizado.Fluxo;
import br.com.condominioauditoria.backend.orcamento.CalculoPrevistoRealizado.Mes;
import br.com.condominioauditoria.backend.orcamento.CalculoPrevistoRealizado.Periodo;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.CodigoAviso;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.Aviso;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.Evidencia;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Monta a entrada do {@link CalculoPrevistoRealizado} a partir do banco e chama a função. Nada é gravado: os números
 * são calculados a cada consulta (ADR 0004, Decisão 5), a partir dos dados já processados (lançamentos, PO, de-para).
 */
@Service
public class ConsultaPrevistoRealizado {

    /** Arquivos de fluxo lidos (com ou sem conferência falhando), como no painel. */
    static final Set<StatusArquivo> FLUXO_LIDO = EnumSet.of(StatusArquivo.CONCLUIDO, StatusArquivo.PRECISA_REVISAO);
    private static final Set<CodigoAviso> AVISOS_DA_PO = EnumSet.of(CodigoAviso.ARREDONDAMENTO,
            CodigoAviso.CONFIRMADA_COM_DIVERGENCIA);

    private final CondominioRepository condominios;
    private final PrevisaoOrcamentariaRepository previsoes;
    private final LinhaPoRepository linhas;
    private final DeparaContaRepository deparas;
    private final PoFundoRepository poFundos;
    private final FundoRepository fundos;
    private final ArquivoRepository arquivos;
    private final LancamentoRepository lancamentos;
    private final ParametroRegraRepository parametros;
    private final ConsultaPrevisao consultaPrevisao;
    private final RealocacaoLancamentoRepository realocacoes;

    ConsultaPrevistoRealizado(CondominioRepository condominios, PrevisaoOrcamentariaRepository previsoes,
            LinhaPoRepository linhas, DeparaContaRepository deparas, PoFundoRepository poFundos,
            FundoRepository fundos, ArquivoRepository arquivos, LancamentoRepository lancamentos,
            ParametroRegraRepository parametros, ConsultaPrevisao consultaPrevisao,
            RealocacaoLancamentoRepository realocacoes) {
        this.condominios = condominios;
        this.previsoes = previsoes;
        this.linhas = linhas;
        this.deparas = deparas;
        this.poFundos = poFundos;
        this.fundos = fundos;
        this.arquivos = arquivos;
        this.lancamentos = lancamentos;
        this.parametros = parametros;
        this.consultaPrevisao = consultaPrevisao;
        this.realocacoes = realocacoes;
    }

    @Transactional(readOnly = true)
    public PrevistoRealizado consultar(UUID condominioId, String periodo, UUID poId) {
        return calcular(condominioId, periodo, poId).resultado();
    }

    /** Lançamentos que compõem um número: "linha:&lt;id&gt;", "fundo:&lt;id&gt;", AJUSTES, A_REALOCAR, SEM_LINHA_PO. */
    @Transactional(readOnly = true)
    public List<Evidencia> evidencia(UUID condominioId, String periodo, UUID poId, String alvo) {
        if (alvo == null || alvo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o alvo da evidência");
        }
        return calcular(condominioId, periodo, poId).evidencias().getOrDefault(alvo.trim(), List.of());
    }

    Calculo calcular(UUID condominioId, String periodoTexto, UUID poId) {
        Condominio condominio = condominios.findById(condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        Periodo periodo = periodo(periodoTexto);
        PrevisaoOrcamentaria po = escolherPo(condominioId, periodo, poId).orElse(null);
        if (po == null) {
            return CalculoPrevistoRealizado.calcular(new Entrada(null, null, null, null, null, null,
                    condominio.getFundoOrdinarioId(), null, null, null, null, null, periodo));
        }
        List<LinhaPo> lidas = linhas.findByPrevisaoIdOrderByOrdem(po.getId());
        Map<UUID, UUID> fundoPorLinha = poFundos.findByPrevisaoId(po.getId()).stream()
                .collect(Collectors.toMap(PoFundo::getLinhaPoId, PoFundo::getFundoId));
        Map<UUID, String> nomes = fundos.findByCondominioId(condominioId).stream()
                .collect(Collectors.toMap(Fundo::getId, Fundo::getNome));
        List<Fluxo> fluxos = arquivos.findByCondominioIdAndCategoriaAndStatusIn(condominioId, Categoria.BALANCETE,
                        FLUXO_LIDO).stream()
                .filter(a -> a.getPeriodoInicio() != null && a.getPeriodoFim() != null)
                .map(a -> new Fluxo(a.getId(), a.getNomeOriginal(), a.getSha256(), a.getPeriodoInicio(),
                        a.getPeriodoFim(), a.getEnviadoEm(), a.getEnviadoPor()))
                .sorted(Comparator.comparing(Fluxo::arquivoId)).toList();

        LocalDate inicio;
        LocalDate fim;
        var vigencia = VigenciaPo.de(po);
        if (periodo instanceof Mes m) {
            inicio = m.mes().atDay(1);
            fim = m.mes().atEndOfMonth();
        } else if (vigencia.isPresent()) {
            inicio = vigencia.get().inicio().atDay(1);
            fim = vigencia.get().fim().atEndOfMonth();
        } else {
            inicio = LocalDate.MIN;
            fim = LocalDate.MIN;
        }
        List<Lancamento> doPeriodo = fluxos.isEmpty() || inicio.equals(LocalDate.MIN) ? List.of()
                : lancamentos.findByArquivoIdInAndDataBetween(fluxos.stream().map(Fluxo::arquivoId).toList(), inicio,
                        fim);
        BigDecimal limite = po.getExercicioInicio() == null ? null
                : parametros.vigente(condominioId, RegraExcessoMes.PARAMETRO, inicio.equals(LocalDate.MIN)
                        ? po.getExercicioInicio().atDay(1) : inicio).map(ParametroRegra::getValor).orElse(null);
        List<Aviso> avisosDaPo = consultaPrevisao.detalhe(po).avisos().stream()
                .filter(a -> AVISOS_DA_PO.contains(a.codigo())).map(a -> new Aviso(a.codigo().name(), a.texto()))
                .toList();
        String nomeArquivo = arquivos.findById(po.getArquivoId()).map(Arquivo::getNomeOriginal).orElse(null);
        // Realocações ativas desta versão da PO (RF-03.1.7), religadas aos lançamentos pela chave estável
        List<CalculoPrevistoRealizado.Realocacao> ativas = realocacoes.findByPrevisaoIdAndDesfeitaEmIsNull(po.getId())
                .stream().map(RealocacaoLancamento::paraCalculo).toList();
        return CalculoPrevistoRealizado.calcular(new Entrada(po, nomeArquivo, lidas,
                deparas.findByPrevisaoIdOrderByContaCodigo(po.getId()), fundoPorLinha, nomes,
                condominio.getFundoOrdinarioId(), fluxos, doPeriodo, ativas, limite, avisosDaPo, periodo));
    }

    private Optional<PrevisaoOrcamentaria> escolherPo(UUID condominioId, Periodo periodo, UUID poId) {
        if (poId != null) {
            return Optional.of(previsoes.findByIdAndCondominioId(poId, condominioId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO não encontrada")));
        }
        if (periodo instanceof Mes m) {
            return consultaPrevisao.vigenteNoMes(condominioId, m.mes());
        }
        // Acumulado sem PO informada: a versão confirmada mais recente
        return previsoes.findByCondominioIdAndEstadoIn(condominioId, EnumSet.of(EstadoPrevisao.CONFIRMADA)).stream()
                .filter(p -> p.getVersao() != null).max(Comparator.comparing(PrevisaoOrcamentaria::getVersao));
    }

    static Periodo periodo(String texto) {
        String t = texto == null ? "" : texto.trim();
        if (t.equalsIgnoreCase("acumulado")) {
            return new Acumulado();
        }
        try {
            return new Mes(YearMonth.parse(t));
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Período deve ser AAAA-MM (ex.: 2026-09) ou \"acumulado\": " + texto);
        }
    }
}
