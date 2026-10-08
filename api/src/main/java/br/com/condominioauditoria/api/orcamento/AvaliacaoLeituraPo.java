package br.com.condominioauditoria.api.orcamento;

import static br.com.condominioauditoria.api.orcamento.DinheiroBr.formatar;

import br.com.condominioauditoria.api.orcamento.EstruturaPo.Grupo;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Decide, no backend, o que as conferências da PO feitas pelo rag significam (RF-03.1.2). Função pura: mesmas linhas,
 * mesmas conferências e mesma tolerância dão sempre o mesmo resultado, na gravação e na consulta.
 *
 * <ul>
 *   <li>Conferência que passou: {@link Classificacao#OK}.</li>
 *   <li>{@code CODIGO_REPETIDO}: pendência resolvida pelo código efetivo na confirmação; não é divergência de soma.</li>
 *   <li>{@code SUBTOTAL_GRUPO}, {@code TOTAL} e {@code PREVISTO_MES} que falharam: o backend refaz a soma pelas
 *       linhas gravadas. Diferença diferente de zero e até a tolerância é {@link Classificacao#ARREDONDAMENTO}
 *       (aviso); acima dela, ou se a soma refeita não confirma a falha, é {@link Classificacao#DIVERGENCIA}.</li>
 *   <li>Qualquer outra conferência que falhou é divergência.</li>
 * </ul>
 *
 * Há divergência → estado {@link EstadoPrevisao#LIDA_COM_DIVERGENCIA}; senão, {@link EstadoPrevisao#LIDA}.
 */
public final class AvaliacaoLeituraPo {

    public static final String SUBTOTAL_GRUPO = "SUBTOTAL_GRUPO";
    public static final String TOTAL = "TOTAL";
    public static final String PREVISTO_MES = "PREVISTO_MES";
    public static final String CODIGO_REPETIDO = "CODIGO_REPETIDO";

    public enum Classificacao {
        OK, ARREDONDAMENTO, DIVERGENCIA, CODIGO_REPETIDO
    }

    /** Conferência como veio do rag. */
    public record ConferenciaPo(String codigo, String descricao, boolean ok, String detalhe) {
    }

    /** Conferência com a classificação do backend e, quando falhou, o texto com as duas somas. */
    public record ConferenciaAvaliada(ConferenciaPo conferencia, Classificacao classificacao, String explicacao) {
    }

    public record Resultado(EstadoPrevisao estado, List<ConferenciaAvaliada> conferencias) {

        public List<String> arredondamentos() {
            return explicacoes(Classificacao.ARREDONDAMENTO);
        }

        public List<String> divergencias() {
            return explicacoes(Classificacao.DIVERGENCIA);
        }

        public boolean temCodigoRepetido() {
            return conferencias.stream().anyMatch(c -> c.classificacao() == Classificacao.CODIGO_REPETIDO);
        }

        private List<String> explicacoes(Classificacao c) {
            return conferencias.stream().filter(a -> a.classificacao() == c).map(ConferenciaAvaliada::explicacao)
                    .toList();
        }
    }

    private AvaliacaoLeituraPo() {
    }

    public static Resultado avaliar(EstruturaPo estrutura, List<ConferenciaPo> conferencias, BigDecimal tolerancia) {
        List<ConferenciaAvaliada> avaliadas = new ArrayList<>();
        int indiceGrupo = 0;
        for (ConferenciaPo c : conferencias) {
            Grupo grupo = null;
            if (SUBTOTAL_GRUPO.equals(c.codigo())) {
                // O rag emite uma SUBTOTAL_GRUPO por grupo, na ordem do documento
                grupo = indiceGrupo < estrutura.grupos().size() ? estrutura.grupos().get(indiceGrupo) : null;
                indiceGrupo++;
            }
            avaliadas.add(avaliar(c, grupo, estrutura, tolerancia));
        }
        boolean divergente = avaliadas.stream().anyMatch(a -> a.classificacao() == Classificacao.DIVERGENCIA);
        return new Resultado(divergente ? EstadoPrevisao.LIDA_COM_DIVERGENCIA : EstadoPrevisao.LIDA,
                List.copyOf(avaliadas));
    }

    private static ConferenciaAvaliada avaliar(ConferenciaPo c, Grupo grupo, EstruturaPo e, BigDecimal tolerancia) {
        if (c.ok()) {
            return new ConferenciaAvaliada(c, Classificacao.OK, null);
        }
        return switch (c.codigo()) {
            case CODIGO_REPETIDO -> new ConferenciaAvaliada(c, Classificacao.CODIGO_REPETIDO, c.detalhe());
            case SUBTOTAL_GRUPO -> grupo == null
                    ? divergencia(c, c.detalhe())
                    : porDiferenca(c, grupo.diferenca(), tolerancia, "%s impresso %s; soma das linhas %s".formatted(
                            grupo.nome(), formatar(grupo.linha().getOrcado()), formatar(grupo.somaDasLinhas())));
            case TOTAL -> e.total() == null
                    ? divergencia(c, c.detalhe())
                    : porDiferenca(c, e.total().getOrcado().subtract(e.somaDosSubtotaisImpressos()), tolerancia,
                            "Total impresso %s; soma dos grupos %s".formatted(formatar(e.total().getOrcado()),
                                    formatar(e.somaDosSubtotaisImpressos())));
            case PREVISTO_MES -> e.previstoMesImpresso()
                    .map(p -> porDiferenca(c, p.subtract(e.somaDosSubtotaisImpressosSemFundos()), tolerancia,
                            "Previsto do mês impresso (total menos fundos) %s; soma dos demais grupos %s"
                                    .formatted(formatar(p), formatar(e.somaDosSubtotaisImpressosSemFundos()))))
                    .orElseGet(() -> divergencia(c, c.detalhe()));
            default -> divergencia(c, c.detalhe());
        };
    }

    private static ConferenciaAvaliada porDiferenca(ConferenciaPo c, BigDecimal diferenca, BigDecimal tolerancia,
            String somas) {
        if (diferenca.signum() == 0) {
            // A soma refeita bate, mas o rag apontou falha: não há como explicar por arredondamento
            return divergencia(c, somas + " (" + c.detalhe() + ")");
        }
        if (diferenca.abs().compareTo(tolerancia) <= 0) {
            return new ConferenciaAvaliada(c, Classificacao.ARREDONDAMENTO, somas + "; diferença de "
                    + formatar(diferenca.abs()) + " tratada como arredondamento; os cálculos usam a soma das linhas");
        }
        return divergencia(c, somas);
    }

    private static ConferenciaAvaliada divergencia(ConferenciaPo c, String explicacao) {
        return new ConferenciaAvaliada(c, Classificacao.DIVERGENCIA, explicacao);
    }
}
