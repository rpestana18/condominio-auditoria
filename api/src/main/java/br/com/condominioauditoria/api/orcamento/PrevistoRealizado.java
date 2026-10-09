package br.com.condominioauditoria.api.orcamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Resultado do previsto × realizado (RF-03.1.6 a RF-03.1.11), o mesmo objeto para a tela, a exportação e o golden.
 * Imutável e sem nada calculado na leitura: tudo sai da {@link CalculoPrevistoRealizado}. Dinheiro em BigDecimal com
 * 2 casas; percentuais com 1 casa (meio para cima), nulos quando o previsto é zero ("—").
 *
 * <p>Com {@code situacao} diferente de CALCULADO, os números ficam nulos e {@code mensagem} diz o porquê: o sistema
 * nunca mostra zero no lugar de um número que não foi apurado (RF-03.1.10).
 */
public record PrevistoRealizado(String versaoCalculo, String periodo, Situacao situacao, String mensagem, PoResumo po,
        List<MesExercicio> meses, List<String> mesesSomados, List<String> mesesSemFluxo,
        List<String> mesesComDoisFluxos, ResumoDeparaPeriodo depara, boolean provisorio, Totais totais,
        List<GrupoResultado> grupos, Bloco ajustes, Bloco aRealocar, Bloco semLinhaPo, ConferenciaFluxo conferencia,
        Regra20 regra20, List<FundoResultado> fundos, List<Aviso> avisos) {

    public enum Situacao {
        CALCULADO, SEM_PO, PO_NAO_CONFIRMADA, SEM_FUNDO_ORDINARIO, SEM_FLUXO, DOIS_FLUXOS
    }

    public enum SituacaoMes {
        COM_FLUXO, SEM_FLUXO, DOIS_FLUXOS
    }

    public enum SituacaoFundo {
        /** Ligado a uma linha 1.9.x: arrecadação × previsto. */
        COMPARADO,
        /** Rateio à parte e demais fundos sem linha na PO: só a movimentação, sem diferença (Q22). */
        SEM_PREVISTO_NA_PO,
        /** Linha 1.9.x sem fundo ligado: sem números (RF-03.1.9). */
        LINHA_SEM_FUNDO,
        /** Fluxo gravado antes do recebimento de cota (v2): reprocesse o fluxo. */
        REPROCESSAR_FLUXO
    }

    public record PoResumo(UUID id, Integer versao, EstadoPrevisao estado, UUID arquivoId, String arquivoNome,
            String sha256, String exercicioInicio, String exercicioFim) {
    }

    public record FluxoUsado(UUID arquivoId, String nome, String sha256, LocalDate periodoInicio, LocalDate periodoFim,
            Instant enviadoEm, String enviadoPor) {
    }

    /**
     * Um mês do exercício (a tela mostra os 12). Os números só existem com um fluxo, e só um. {@code prorrogado}: mês
     * depois do exercício em que a PO vale por prorrogação (RF-11.3); no acumulado vem depois dos 12, fora da soma.
     */
    public record MesExercicio(String mes, SituacaoMes situacao, List<FluxoUsado> fluxos, BigDecimal previsto,
            BigDecimal despesaRealizada, BigDecimal excesso, BigDecimal percentualExcesso, Boolean acimaDoLimite,
            boolean prorrogado) {

        MesExercicio comProrrogado(boolean valor) {
            return new MesExercicio(mes, situacao, fluxos, previsto, despesaRealizada, excesso, percentualExcesso,
                    acimaDoLimite, valor);
        }
    }

    /** "N de M contas confirmadas": contas com débito no fundo Condomínio no período. */
    public record ResumoDeparaPeriodo(int contas, int confirmadas, int semDeparaConfirmado) {
    }

    /**
     * @param previsto previsto do período (previsto do mês × meses somados)
     * @param despesaRealizada em linhas + a realocar + sem linha da PO (sem ajustes e sem transferências)
     * @param previstoExercicio referência: previsto do mês × meses do exercício
     */
    public record Totais(BigDecimal previstoMes, BigDecimal previsto, BigDecimal despesaRealizada, BigDecimal emLinhas,
            BigDecimal diferenca, BigDecimal execucao, BigDecimal previstoExercicio) {
    }

    public record LinhaResultado(UUID linhaId, String codigo, String descricao, String conta, MarcaPo marca,
            String observacoes, int pagina, BigDecimal previsto, BigDecimal realizado, BigDecimal diferenca,
            BigDecimal execucao, List<String> contasFluxo, int lancamentos) {
    }

    public record GrupoResultado(UUID linhaId, String codigo, String descricao, BigDecimal previsto,
            BigDecimal realizado, BigDecimal diferenca, BigDecimal execucao, List<LinhaResultado> linhas) {
    }

    public record ContaBloco(String conta, String nome, String detalhe, BigDecimal valor, int lancamentos) {
    }

    /** Bloco à parte, fora das linhas (ajustes, a realocar, sem linha da PO). */
    public record Bloco(BigDecimal total, int lancamentos, List<ContaBloco> contas) {
    }

    /** Total de débitos do fundo = despesa realizada + ajustes + transferências (RF-03.1.6). */
    public record ConferenciaFluxo(BigDecimal debitosDoFundo, int lancamentos, BigDecimal despesaRealizada,
            BigDecimal ajustes, BigDecimal transferencias, boolean confere) {
    }

    public record LinhaExcesso(UUID linhaId, String codigo, String descricao, BigDecimal excesso) {
    }

    /**
     * Regra dos 20% (só no mês). {@code limite} arredondado para centavos; a comparação é exata (MonthlyOverrunRule).
     * {@code cenarioMaximo} = excesso + a realocar + sem linha da PO (Q26).
     */
    public record Regra20(String regra, String versaoRegra, BigDecimal limitePercentual, BigDecimal previstoMes,
            BigDecimal excesso, BigDecimal percentual, BigDecimal limite, int linhasAcima, List<LinhaExcesso> linhas,
            BigDecimal aRealocar, BigDecimal semLinhaPo, BigDecimal cenarioMaximo, BigDecimal percentualCenarioMaximo,
            boolean provisorio, boolean acimaDoLimite) {
    }

    /**
     * Fundo de reserva, de obras e demais. {@code previsto}, {@code arrecadado}, {@code diferenca} e {@code execucao}
     * só com COMPARADO; {@code creditos} e {@code debitos} são a movimentação do período.
     */
    public record FundoResultado(UUID fundoId, String fundo, UUID linhaId, String linhaCodigo, SituacaoFundo situacao,
            BigDecimal previsto, BigDecimal arrecadado, BigDecimal diferenca, BigDecimal execucao, BigDecimal creditos,
            BigDecimal debitos) {
    }

    public record Aviso(String codigo, String texto) {
    }

    /** Lançamento que compõe um número (RF-03.1.12), com arquivo, página e hash de origem. */
    public record Evidencia(UUID lancamentoId, LocalDate data, String conta, String contaNome, String historico,
            String fornecedor, String documento, BigDecimal valor, String fundo, UUID arquivoId, String arquivoNome,
            String sha256, int pagina, int ordem, String realocacao, UUID realocacaoId) {
    }
}
