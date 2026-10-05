package br.com.condominioauditoria.backend.modulo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Custo estimado do uso, em US$ (RF-09.7; ADR 0003, Decisão 4): tokens × preço do catálogo do rag por milhão de
 * tokens, por provedor e modelo. O custo não é gravado por registro: é calculado aqui, no relatório do período, em
 * BigDecimal exato, e arredondado para 2 casas (meio para cima) só em cada total exibido (mês e função, função no
 * período e total geral). Assim, somar os totais arredondados pode diferir de 1 centavo do total geral, que é o
 * arredondamento da soma exata. Mesmos registros e mesmos preços = mesmo resultado.
 *
 * Tokens de um modelo sem preço no catálogo deixam o total daquele grupo (e o total geral) sem valor, e o modelo é
 * listado em modelosSemPreco: nada é estimado com preço inventado.
 */
public final class CustoUso {

    static final int CASAS = 2;
    static final RoundingMode ARREDONDAMENTO = RoundingMode.HALF_UP;
    private static final BigDecimal MILHAO = BigDecimal.valueOf(1_000_000);

    private CustoUso() {
    }

    /** Preço em US$ por milhão de tokens de entrada e de saída. */
    public record PrecoModelo(BigDecimal entradaMilhaoUsd, BigDecimal saidaMilhaoUsd) {

        public PrecoModelo {
            Objects.requireNonNull(entradaMilhaoUsd);
            Objects.requireNonNull(saidaMilhaoUsd);
        }

        public static String chave(String provedor, String modelo) {
            return (provedor == null ? "" : provedor) + "/" + (modelo == null ? "" : modelo);
        }
    }

    /** Tokens somados de um mês, módulo, função, provedor e modelo (saída da consulta do repositório). */
    public record TokensPorModelo(String mes, String modulo, FuncaoUso funcao, String provedor, String modelo,
            long tokensEntrada, long tokensSaida) {
    }

    /**
     * Resultado: custo por mês+módulo+função ({@link #chaveMes}) e por módulo+função no período ({@link #chaveFuncao}),
     * e o total. Valor nulo = há tokens de modelo sem preço nesse grupo. Grupo sem tokens não aparece.
     */
    public record CustoDoPeriodo(Map<String, BigDecimal> porMes, Map<String, BigDecimal> porFuncao, BigDecimal total,
            Set<String> modelosSemPreco) {

        public static String chaveMes(String mes, String modulo, FuncaoUso funcao) {
            return mes + "|" + modulo + "|" + funcao.codigo();
        }

        public static String chaveFuncao(String modulo, FuncaoUso funcao) {
            return modulo + "|" + funcao.codigo();
        }

        public BigDecimal doMes(TotalUso t) {
            return porMes.get(chaveMes(t.mes(), t.modulo(), t.funcao()));
        }

        public BigDecimal daFuncao(TotalUso t) {
            return porFuncao.get(chaveFuncao(t.modulo(), t.funcao()));
        }
    }

    public static CustoDoPeriodo calcular(List<TokensPorModelo> linhas, Map<String, PrecoModelo> precos) {
        Map<String, BigDecimal> exatoMes = new LinkedHashMap<>();
        Map<String, BigDecimal> exatoFuncao = new LinkedHashMap<>();
        Set<String> semPrecoMes = new TreeSet<>();
        Set<String> semPrecoFuncao = new TreeSet<>();
        Set<String> modelosSemPreco = new TreeSet<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean totalSemPreco = false;

        for (TokensPorModelo l : linhas) {
            if (l.tokensEntrada() == 0 && l.tokensSaida() == 0) {
                continue;
            }
            String mes = CustoDoPeriodo.chaveMes(l.mes(), l.modulo(), l.funcao());
            String funcao = CustoDoPeriodo.chaveFuncao(l.modulo(), l.funcao());
            exatoMes.putIfAbsent(mes, BigDecimal.ZERO);
            exatoFuncao.putIfAbsent(funcao, BigDecimal.ZERO);
            PrecoModelo preco = precos.get(PrecoModelo.chave(l.provedor(), l.modelo()));
            if (preco == null) {
                modelosSemPreco.add(PrecoModelo.chave(l.provedor(), l.modelo()));
                semPrecoMes.add(mes);
                semPrecoFuncao.add(funcao);
                totalSemPreco = true;
                continue;
            }
            BigDecimal custo = exato(l.tokensEntrada(), l.tokensSaida(), preco);
            exatoMes.merge(mes, custo, BigDecimal::add);
            exatoFuncao.merge(funcao, custo, BigDecimal::add);
            total = total.add(custo);
        }
        return new CustoDoPeriodo(arredondar(exatoMes, semPrecoMes), arredondar(exatoFuncao, semPrecoFuncao),
                totalSemPreco ? null : arredondar(total), modelosSemPreco);
    }

    /** Custo exato, sem arredondar: (entrada × preço de entrada + saída × preço de saída) / 1.000.000. */
    static BigDecimal exato(long tokensEntrada, long tokensSaida, PrecoModelo preco) {
        return BigDecimal.valueOf(tokensEntrada).multiply(preco.entradaMilhaoUsd())
                .add(BigDecimal.valueOf(tokensSaida).multiply(preco.saidaMilhaoUsd()))
                .divide(MILHAO); // divisão por 10^6 é sempre exata
    }

    static BigDecimal arredondar(BigDecimal valor) {
        return valor.setScale(CASAS, ARREDONDAMENTO);
    }

    private static Map<String, BigDecimal> arredondar(Map<String, BigDecimal> exatos, Set<String> semPreco) {
        Map<String, BigDecimal> saida = new LinkedHashMap<>();
        exatos.forEach((chave, valor) -> saida.put(chave, semPreco.contains(chave) ? null : arredondar(valor)));
        return saida;
    }
}
