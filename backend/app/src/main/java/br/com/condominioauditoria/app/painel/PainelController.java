package br.com.condominioauditoria.app.painel;

import br.com.condominioauditoria.app.arquivo.Arquivo;
import br.com.condominioauditoria.app.arquivo.ArquivoRepository;
import br.com.condominioauditoria.app.contabil.ConferenciaRepository;
import br.com.condominioauditoria.app.contabil.Fundo;
import br.com.condominioauditoria.app.contabil.FundoRepository;
import br.com.condominioauditoria.app.contabil.Lancamento;
import br.com.condominioauditoria.app.contabil.LancamentoRepository;
import br.com.condominioauditoria.app.contabil.SaldoFundo;
import br.com.condominioauditoria.app.contabil.SaldoFundoRepository;
import br.com.condominioauditoria.app.seguranca.AcessoCondominio;
import br.com.condominioauditoria.dominio.StatusArquivo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Números da tela inicial, lidos do que já está gravado (nada é recalculado a cada visualização).
 * Fonte: o fluxo de caixa mais recente.
 */
@RestController
@RequestMapping("/api/condominios/{condominioId}/painel")
class PainelController {

    private static final String FLUXO = "fluxo-caixa-protest";

    private final AcessoCondominio acesso;
    private final ArquivoRepository arquivos;
    private final SaldoFundoRepository saldos;
    private final FundoRepository fundos;
    private final LancamentoRepository lancamentos;
    private final ConferenciaRepository conferencias;

    PainelController(AcessoCondominio acesso, ArquivoRepository arquivos, SaldoFundoRepository saldos,
            FundoRepository fundos, LancamentoRepository lancamentos, ConferenciaRepository conferencias) {
        this.acesso = acesso;
        this.arquivos = arquivos;
        this.saldos = saldos;
        this.fundos = fundos;
        this.lancamentos = lancamentos;
        this.conferencias = conferencias;
    }

    @GetMapping
    ResponseEntity<Painel> painel(@PathVariable UUID condominioId) {
        acesso.exigir(condominioId);
        return arquivos.findFirstByCondominioIdAndInterpretadorAndStatusInOrderByPeriodoFimDescEnviadoEmDesc(
                        condominioId, FLUXO, List.of(StatusArquivo.CONCLUIDO, StatusArquivo.PRECISA_REVISAO))
                .map(a -> ResponseEntity.ok(montar(condominioId, a)))
                .orElse(ResponseEntity.noContent().build());
    }

    private Painel montar(UUID condominioId, Arquivo arquivo) {
        Map<UUID, String> nomes = fundos.findByCondominioId(condominioId).stream()
                .collect(Collectors.toMap(Fundo::getId, Fundo::getNome));
        List<SaldoFundo> posicao = saldos.findByArquivoId(arquivo.getId());
        List<FundoNoPeriodo> porFundo = posicao.stream()
                .map(s -> new FundoNoPeriodo(nomes.get(s.getFundoId()), s.getSaldoAnterior(), s.getCreditos(),
                        s.getDebitos(), s.getCreditos().subtract(s.getDebitos()), s.getSaldoAtual()))
                .sorted(Comparator.comparing(FundoNoPeriodo::saldoAtual).reversed())
                .toList();
        List<Despesa> maiores = lancamentos
                .findByArquivoIdAndTransferenciaEntreFundosFalseOrderByDebitoDesc(arquivo.getId(), Limit.of(10)).stream()
                .map(l -> Despesa.de(l, nomes))
                .toList();
        long conferenciasComFalha = conferencias.findByArquivoIdOrderByOrdem(arquivo.getId()).stream()
                .filter(c -> !c.isOk()).count();
        return new Painel(arquivo.getId(), arquivo.getNomeOriginal(), arquivo.getPeriodoInicio(), arquivo.getPeriodoFim(),
                soma(posicao, SaldoFundo::getSaldoAnterior), soma(posicao, SaldoFundo::getCreditos),
                soma(posicao, SaldoFundo::getDebitos), soma(posicao, SaldoFundo::getSaldoAtual),
                conferenciasComFalha, porFundo, maiores);
    }

    private static BigDecimal soma(List<SaldoFundo> lista, Function<SaldoFundo, BigDecimal> campo) {
        return lista.stream().map(campo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    record Painel(UUID arquivoId, String arquivoNome, LocalDate periodoInicio, LocalDate periodoFim,
            BigDecimal saldoAnterior, BigDecimal entradas, BigDecimal saidas, BigDecimal saldoAtual,
            long conferenciasComFalha, List<FundoNoPeriodo> fundos, List<Despesa> maioresDespesas) {
    }

    record FundoNoPeriodo(String fundo, BigDecimal saldoAnterior, BigDecimal entradas, BigDecimal saidas,
            BigDecimal resultado, BigDecimal saldoAtual) {
    }

    record Despesa(LocalDate data, String fundo, String conta, String historico, BigDecimal valor, int pagina) {
        static Despesa de(Lancamento l, Map<UUID, String> nomes) {
            String conta = l.getContaCodigo() == null ? l.getContaNome() : l.getContaCodigo() + " " + l.getContaNome();
            return new Despesa(l.getData(), nomes.get(l.getFundoId()), conta, l.getHistorico(), l.getDebito(), l.getPagina());
        }
    }
}
