package br.com.condominioauditoria.rag.dominio.fluxo;

import java.time.LocalDate;
import java.util.List;

/** Fluxo de caixa mensal da administradora, já interpretado, ainda sem nenhum juízo de auditoria. */
public record FluxoDeCaixa(
        String empreendimento,
        LocalDate periodoInicio,
        LocalDate periodoFim,
        List<SecaoFundo> secoes,
        List<PosicaoFundo> posicaoFinanceira,
        PosicaoFundo totalPosicao) {

    public int totalLancamentos() {
        return secoes.stream().mapToInt(s -> s.lancamentos().size()).sum();
    }
}
