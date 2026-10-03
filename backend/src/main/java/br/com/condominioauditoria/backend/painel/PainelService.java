package br.com.condominioauditoria.backend.painel;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import br.com.condominioauditoria.backend.arquivo.ArquivoRepository;
import br.com.condominioauditoria.backend.contabil.ConferenciaRepository;
import br.com.condominioauditoria.backend.contabil.Fundo;
import br.com.condominioauditoria.backend.contabil.FundoRepository;
import br.com.condominioauditoria.backend.contabil.Lancamento;
import br.com.condominioauditoria.backend.contabil.LancamentoRepository;
import br.com.condominioauditoria.backend.contabil.SaldoFundo;
import br.com.condominioauditoria.backend.contabil.SaldoFundoRepository;
import br.com.condominioauditoria.backend.arquivo.StatusArquivo;
import br.com.condominioauditoria.backend.condominio.Condominio;
import br.com.condominioauditoria.backend.condominio.CondominioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Números da tela inicial, lidos do que já está gravado (nada é recalculado a cada visualização).
 * Fonte: o fluxo de caixa mais recente. Usado pela API REST (tela) e pelo gRPC (mcp): o número é o mesmo nos dois.
 * Quem chama confere o acesso ao condomínio antes.
 */
@Service
public class PainelService {

    private static final String FLUXO = "fluxo-caixa-protest";

    private final ArquivoRepository arquivos;
    private final SaldoFundoRepository saldos;
    private final FundoRepository fundos;
    private final LancamentoRepository lancamentos;
    private final ConferenciaRepository conferencias;
    private final CondominioRepository condominios;

    PainelService(ArquivoRepository arquivos, SaldoFundoRepository saldos, FundoRepository fundos,
            LancamentoRepository lancamentos, ConferenciaRepository conferencias, CondominioRepository condominios) {
        this.arquivos = arquivos;
        this.condominios = condominios;
        this.saldos = saldos;
        this.fundos = fundos;
        this.lancamentos = lancamentos;
        this.conferencias = conferencias;
    }

    @Transactional(readOnly = true)
    public Optional<Painel> ultimo(UUID condominioId) {
        return arquivos.findFirstByCondominioIdAndInterpretadorAndStatusInOrderByPeriodoFimDescEnviadoEmDesc(
                        condominioId, FLUXO, List.of(StatusArquivo.CONCLUIDO, StatusArquivo.PRECISA_REVISAO))
                .map(a -> montar(condominioId, a));
    }

    private Painel montar(UUID condominioId, Arquivo arquivo) {
        Map<UUID, String> nomes = fundos.findByCondominioId(condominioId).stream()
                .collect(Collectors.toMap(Fundo::getId, Fundo::getNome));
        List<SaldoFundo> posicao = saldos.findByArquivoId(arquivo.getId());
        List<FundoNoPeriodo> porFundo = posicao.stream()
                .map(s -> new FundoNoPeriodo(s.getFundoId(), nomes.get(s.getFundoId()), s.getSaldoAnterior(),
                        s.getCreditos(), s.getDebitos(), s.getCreditos().subtract(s.getDebitos()), s.getSaldoAtual()))
                .sorted(Comparator.comparing(FundoNoPeriodo::saldoAtual).reversed())
                .toList();
        List<Despesa> maiores = lancamentos
                .findByArquivoIdAndTransferenciaEntreFundosFalseOrderByDebitoDesc(arquivo.getId(), Limit.of(10)).stream()
                .map(l -> Despesa.de(l, nomes))
                .toList();
        long conferenciasComFalha = conferencias.findByArquivoIdOrderByOrdem(arquivo.getId()).stream()
                .filter(c -> !c.isOk()).count();
        UUID ordinarioId = condominios.findById(condominioId).map(Condominio::getFundoOrdinarioId).orElse(null);
        FundoOrdinario ordinario = FundoOrdinario.de(ordinarioId, nomes.get(ordinarioId), porFundo).orElse(null);
        return new Painel(arquivo.getId(), arquivo.getNomeOriginal(), arquivo.getPeriodoInicio(), arquivo.getPeriodoFim(),
                soma(posicao, SaldoFundo::getSaldoAnterior), soma(posicao, SaldoFundo::getCreditos),
                soma(posicao, SaldoFundo::getDebitos), soma(posicao, SaldoFundo::getSaldoAtual),
                conferenciasComFalha, ordinario, porFundo, maiores);
    }

    private static BigDecimal soma(List<SaldoFundo> lista, Function<SaldoFundo, BigDecimal> campo) {
        return lista.stream().map(campo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record Painel(UUID arquivoId, String arquivoNome, LocalDate periodoInicio, LocalDate periodoFim,
            BigDecimal saldoAnterior, BigDecimal entradas, BigDecimal saidas, BigDecimal saldoAtual,
            long conferenciasComFalha, FundoOrdinario fundoOrdinario, List<FundoNoPeriodo> fundos,
            List<Despesa> maioresDespesas) {
    }

    public record FundoNoPeriodo(UUID fundoId, String fundo, BigDecimal saldoAnterior, BigDecimal entradas,
            BigDecimal saidas, BigDecimal resultado, BigDecimal saldoAtual) {
    }

    public record Despesa(LocalDate data, String fundo, String conta, String historico, BigDecimal valor, int pagina) {
        static Despesa de(Lancamento l, Map<UUID, String> nomes) {
            String conta = l.getContaCodigo() == null ? l.getContaNome() : l.getContaCodigo() + " " + l.getContaNome();
            return new Despesa(l.getData(), nomes.get(l.getFundoId()), conta, l.getHistorico(), l.getDebito(), l.getPagina());
        }
    }
}
