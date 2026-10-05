package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.orcamento.CalculoPrevistoRealizado.Calculo;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.Aviso;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.MesExercicio;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.Situacao;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Filtro "fundo" do previsto × realizado (RF-03.1.13), aplicado ao resultado já calculado: nada é recalculado, só
 * escondido. Fundo Condomínio (o fundo ordinário): grupos, linhas, blocos, conferência e regra dos 20%, sem o painel
 * dos fundos. Outro fundo: só o painel daquele fundo (arrecadação × previsto, ou movimentação sem previsto) e a
 * evidência dele; os números do fundo Condomínio ficam nulos e o resultado nunca é "provisório". Função pura.
 */
public final class VisaoPorFundo {

    /** Avisos que só dizem respeito ao fundo Condomínio. */
    private static final Set<String> AVISOS_DO_CONDOMINIO = Set.of("SEM_DEPARA_CONFIRMADO", "LANCAMENTO_SEM_CONTA",
            "A_REALOCAR", "REALOCACAO_SEM_LANCAMENTO", "REALOCACAO_SEM_EFEITO", "CONFERENCIA_FLUXO",
            "REGRA_NAO_AVALIADA");
    private static final Set<String> AVISOS_DE_FUNDO = Set.of("LINHA_SEM_FUNDO", "REPROCESSAR_FLUXO");

    private VisaoPorFundo() {
    }

    /** {@code fundoId} nulo: sem filtro (tudo). */
    public static Calculo filtrar(Calculo c, UUID fundoId, UUID fundoOrdinarioId) {
        PrevistoRealizado r = c.resultado();
        if (fundoId == null || r.situacao() != Situacao.CALCULADO) {
            return c;
        }
        if (fundoId.equals(fundoOrdinarioId)) {
            Map<String, List<PrevistoRealizado.Evidencia>> ev = c.evidencias().entrySet().stream()
                    .filter(x -> !x.getKey().startsWith("fundo:"))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a,
                            java.util.TreeMap::new));
            return new Calculo(new PrevistoRealizado(r.versaoCalculo(), r.periodo(), r.situacao(), r.mensagem(), r.po(),
                    r.meses(), r.mesesSomados(), r.mesesSemFluxo(), r.mesesComDoisFluxos(), r.depara(), r.provisorio(),
                    r.totais(), r.grupos(), r.ajustes(), r.aRealocar(), r.semLinhaPo(), r.conferencia(), r.regra20(),
                    List.of(), r.avisos().stream().filter(a -> !AVISOS_DE_FUNDO.contains(a.codigo())).toList()), ev);
        }
        String alvo = CalculoPrevistoRealizado.alvoFundo(fundoId);
        List<Aviso> avisos = r.avisos().stream().filter(a -> !AVISOS_DO_CONDOMINIO.contains(a.codigo())).toList();
        List<MesExercicio> meses = r.meses().stream().map(m -> new MesExercicio(m.mes(), m.situacao(), m.fluxos(),
                null, null, null, null, null)).toList();
        return new Calculo(new PrevistoRealizado(r.versaoCalculo(), r.periodo(), r.situacao(), r.mensagem(), r.po(),
                meses, r.mesesSomados(), r.mesesSemFluxo(), r.mesesComDoisFluxos(), null, false, null, List.of(), null,
                null, null, null, null, r.fundos().stream().filter(f -> fundoId.equals(f.fundoId())).toList(), avisos),
                c.evidencias().containsKey(alvo) ? Map.of(alvo, c.evidencias().get(alvo)) : Map.of());
    }
}
