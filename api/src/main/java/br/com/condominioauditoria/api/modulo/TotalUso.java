package br.com.condominioauditoria.api.modulo;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Soma do uso de uma função de um módulo, num mês (mes = "AAAA-MM") ou no período inteiro (mes nulo). Só contagens:
 * custo fica para a entrega 3 (tokens × preço do catálogo, em BigDecimal).
 */
public record TotalUso(String mes, String modulo, FuncaoUso funcao, long quantidade, long tokensEntrada,
        long tokensSaida, long arquivos, long paginas) {

    private static final Comparator<TotalUso> ORDEM = Comparator.comparing(TotalUso::modulo)
            .thenComparing(TotalUso::funcao);

    /** Junta os meses em um total por módulo e função, na ordem módulo e função (determinístico). */
    public static List<TotalUso> somarPorFuncao(List<TotalUso> porMes) {
        Map<String, TotalUso> totais = new LinkedHashMap<>();
        for (TotalUso t : porMes) {
            totais.merge(t.modulo() + "|" + t.funcao().codigo(),
                    new TotalUso(null, t.modulo(), t.funcao(), t.quantidade(), t.tokensEntrada(), t.tokensSaida(),
                            t.arquivos(), t.paginas()),
                    (a, b) -> new TotalUso(null, a.modulo(), a.funcao(), a.quantidade() + b.quantidade(),
                            a.tokensEntrada() + b.tokensEntrada(), a.tokensSaida() + b.tokensSaida(),
                            a.arquivos() + b.arquivos(), a.paginas() + b.paginas()));
        }
        return totais.values().stream().sorted(ORDEM).toList();
    }
}
