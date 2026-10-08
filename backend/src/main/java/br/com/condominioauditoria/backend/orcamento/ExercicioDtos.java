package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.orcamento.ColunaImpressa.GrupoColuna;
import br.com.condominioauditoria.backend.orcamento.DeparaDtos.ResumoDepara;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.Prorrogacao;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.SituacaoMes;
import br.com.condominioauditoria.backend.orcamento.RubricaDtos.ResumoRubricas;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Respostas da API dos exercícios (RF-11.4 e RF-11.5; contrato em contracts/openapi.yaml). Meses em AAAA-MM. */
public final class ExercicioDtos {

    private ExercicioDtos() {
    }

    /** PO = PO confirmada enviada; COLUNA_IMPRESSA = coluna "Orçado anterior" de uma PO, só com previsto (RF-11.5). */
    public enum TipoExercicio {
        PO, COLUNA_IMPRESSA
    }

    /** Mês do exercício e se há fluxo carregado; {@code prorrogado}: mês depois do exercício, por prorrogação. */
    public record MesDoExercicio(String mes, SituacaoMes situacao, boolean prorrogado) {
    }

    /**
     * Um exercício da lista. {@code id}: "po:&lt;uuid&gt;" ou "coluna:&lt;uuid&gt;" (o uuid é o da PO que imprimiu a
     * coluna), usado na comparação. Na coluna impressa, {@code inicio} e {@code fim} são os 12 meses antes da PO que a
     * imprimiu, {@code meses} vem vazio (sem realizado) e de-para, rubricas e prorrogação vêm nulos.
     * {@code colunaImpressa}: id da coluna que este exercício substituiu e que fica só como conferência (RF-11.5).
     */
    public record Exercicio(String id, TipoExercicio tipo, String rotulo, UUID poId, Integer versao, String inicio,
            String fim, Prorrogacao prorrogacao, BigDecimal previstoMes, List<MesDoExercicio> meses,
            ResumoDepara depara, ResumoRubricas rubricas, String colunaImpressa, List<String> avisos) {
    }

    /**
     * Conferência da coluna "Orçado anterior" de uma PO (RF-11.5). {@code substituida}: há PO anterior confirmada, e a
     * comparação usa ela; {@code diferencas}: grupos em que a PO anterior e a coluna diferem acima da tolerância.
     */
    public record ConferenciaColuna(String id, String rotulo, UUID poId, boolean substituida, UUID poAnteriorId,
            String poAnteriorRotulo, BigDecimal totalImpresso, boolean totalIncluiFundos, BigDecimal fundos,
            BigDecimal previstoMes, List<GrupoColuna> grupos, List<ColunaImpressa.DiferencaGrupo> diferencas,
            List<String> avisos) {
    }
}
