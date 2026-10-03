package br.com.condominioauditoria.rag.dominio.fluxo;

import static br.com.condominioauditoria.rag.dominio.Dinheiro.formatarBr;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Confere se o fluxo de caixa fecha com ele mesmo. É a primeira prova de que a leitura do PDF está correta:
 * se algum número tiver sido lido errado, alguma destas somas não bate.
 */
public final class ConferenciaFluxo {

    private ConferenciaFluxo() {
    }

    public static List<Verificacao> conferir(FluxoDeCaixa fluxo) {
        List<Verificacao> resultado = new ArrayList<>();
        Map<String, PosicaoFundo> posicao = fluxo.posicaoFinanceira().stream()
                .collect(Collectors.toMap(PosicaoFundo::fundo, Function.identity(), (a, b) -> a));

        resultado.add(saldoCorrente(fluxo));
        resultado.add(totaisPorFundo(fluxo));
        resultado.add(saldoFinalPorFundo(fluxo, posicao));
        resultado.add(somaDaPosicao(fluxo));
        return resultado;
    }

    /** Saldo anterior + crédito − débito tem que dar o saldo impresso em cada linha. */
    private static Verificacao saldoCorrente(FluxoDeCaixa fluxo) {
        List<String> erros = new ArrayList<>();
        for (SecaoFundo secao : fluxo.secoes()) {
            BigDecimal saldo = secao.saldoAnterior();
            for (LancamentoFluxo l : secao.lancamentos()) {
                saldo = saldo.add(l.credito()).subtract(l.debito());
                if (saldo.compareTo(l.saldo()) != 0) {
                    erros.add("%s, pág. %d, %s: calculado %s, impresso %s".formatted(
                            secao.fundo(), l.pagina(), l.data(), formatarBr(saldo), formatarBr(l.saldo())));
                    saldo = l.saldo();
                }
            }
        }
        return new Verificacao("SALDO_CORRENTE", "Saldo linha a linha em todos os fundos", erros.isEmpty(),
                erros.isEmpty() ? fluxo.totalLancamentos() + " lançamentos conferidos" : String.join("; ", erros));
    }

    /** Soma dos lançamentos de cada fundo = linha TOTAIS do relatório. */
    private static Verificacao totaisPorFundo(FluxoDeCaixa fluxo) {
        List<String> erros = new ArrayList<>();
        for (SecaoFundo secao : fluxo.secoes()) {
            BigDecimal creditos = soma(secao.lancamentos(), LancamentoFluxo::credito);
            BigDecimal debitos = soma(secao.lancamentos(), LancamentoFluxo::debito);
            if (creditos.compareTo(secao.totalCreditosInformado()) != 0 || debitos.compareTo(secao.totalDebitosInformado()) != 0) {
                erros.add("%s: soma %s / %s, TOTAIS %s / %s".formatted(secao.fundo(), formatarBr(creditos),
                        formatarBr(debitos), formatarBr(secao.totalCreditosInformado()),
                        formatarBr(secao.totalDebitosInformado())));
            }
        }
        return new Verificacao("TOTAIS_FUNDO", "Soma dos lançamentos = linha TOTAIS de cada fundo", erros.isEmpty(),
                erros.isEmpty() ? fluxo.secoes().size() + " fundos conferidos" : String.join("; ", erros));
    }

    /** Saldo anterior + créditos − débitos de cada seção = saldo atual na Posição Financeira. */
    private static Verificacao saldoFinalPorFundo(FluxoDeCaixa fluxo, Map<String, PosicaoFundo> posicao) {
        List<String> erros = new ArrayList<>();
        for (SecaoFundo secao : fluxo.secoes()) {
            PosicaoFundo p = posicao.get(secao.fundo());
            if (p == null) {
                erros.add(secao.fundo() + ": não aparece na Posição Financeira");
                continue;
            }
            BigDecimal calculado = secao.saldoAnterior()
                    .add(soma(secao.lancamentos(), LancamentoFluxo::credito))
                    .subtract(soma(secao.lancamentos(), LancamentoFluxo::debito));
            if (calculado.compareTo(p.saldoAtual()) != 0 || secao.saldoAnterior().compareTo(p.saldoAnterior()) != 0) {
                erros.add("%s: calculado %s, Posição Financeira %s".formatted(secao.fundo(), formatarBr(calculado),
                        formatarBr(p.saldoAtual())));
            }
        }
        return new Verificacao("SALDO_FINAL_FUNDO", "Saldo final de cada fundo = Posição Financeira", erros.isEmpty(),
                erros.isEmpty() ? fluxo.secoes().size() + " fundos conferidos" : String.join("; ", erros));
    }

    /** Soma das linhas da Posição Financeira = linha TOTAL. */
    private static Verificacao somaDaPosicao(FluxoDeCaixa fluxo) {
        PosicaoFundo total = fluxo.totalPosicao();
        List<PosicaoFundo> linhas = fluxo.posicaoFinanceira();
        boolean ok = total != null
                && soma(linhas, PosicaoFundo::saldoAnterior).compareTo(total.saldoAnterior()) == 0
                && soma(linhas, PosicaoFundo::creditos).compareTo(total.creditos()) == 0
                && soma(linhas, PosicaoFundo::debitos).compareTo(total.debitos()) == 0
                && soma(linhas, PosicaoFundo::saldoAtual).compareTo(total.saldoAtual()) == 0;
        String detalhe = total == null ? "Linha TOTAL não encontrada"
                : "Saldo anterior %s + créditos %s − débitos %s = %s".formatted(formatarBr(total.saldoAnterior()),
                        formatarBr(total.creditos()), formatarBr(total.debitos()), formatarBr(total.saldoAtual()));
        return new Verificacao("TOTAL_POSICAO", "Soma dos fundos = TOTAL da Posição Financeira", ok, detalhe);
    }

    private static <T> BigDecimal soma(List<T> itens, Function<T, BigDecimal> campo) {
        return itens.stream().map(campo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
