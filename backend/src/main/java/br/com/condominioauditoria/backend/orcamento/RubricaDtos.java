package br.com.condominioauditoria.backend.orcamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Pedidos e respostas da API das rubricas (contrato em contracts/openapi.yaml). */
public final class RubricaDtos {

    private RubricaDtos() {
    }

    /** Filtros da tela de rubricas de uma PO. PENDENTES = sem rubrica confirmada (sem, sugerida ou recusada). */
    public enum FiltroRubrica {
        TODAS, PENDENTES, SUGERIDO, CONFIRMADO, RECUSADO, SEM_RUBRICA
    }

    public record RubricaDto(UUID id, String nome, String grupo, UUID linhaOrigemId, String criadaPor,
            Instant criadaEm) {

        static RubricaDto de(Rubrica r) {
            return new RubricaDto(r.getId(), r.getNome(), r.getGrupoCodigo(), r.getLinhaOrigemId(), r.getCriadaPor(),
                    r.getCriadaEm());
        }
    }

    /**
     * Uma linha da PO e a sua rubrica. {@code estado} nulo: linha sem rubrica. {@code conta}: o que a sugestão compara
     * (conta da PO, texto da coluna de conta ou, sem os dois, a descrição).
     */
    public record LinhaComRubrica(UUID linhaId, String codigo, String grupo, String conta, String descricao,
            BigDecimal orcado, RubricaDto rubrica, EstadoRubrica estado, OrigemRubrica origem, String motivo,
            String atualizadoPor, Instant atualizadoEm) {
    }

    public record ResumoRubricas(int linhas, int confirmadas, int sugeridas, int recusadas, int semRubrica) {
    }

    public record RubricasDaPo(UUID previsaoId, Integer versao, ResumoRubricas resumo, List<LinhaComRubrica> linhas) {
    }

    /** Nova rubrica no catálogo. {@code grupo} opcional (ex.: "1.3"). */
    public record PedidoNovaRubrica(String nome, String grupo) {
    }

    public record PedidoRenomear(String nome) {
    }

    /**
     * Rubrica escolhida pelo Admin para uma linha: uma existente ({@code rubricaId}) ou uma nova criada a partir da
     * linha ({@code novaRubrica}, com o nome). {@code confirmar} nulo = true.
     */
    public record PedidoRubricaLinha(UUID rubricaId, String novaRubrica, Boolean confirmar) {
    }

    public enum AcaoLoteRubrica {
        CONFIRMAR, RECUSAR
    }

    public record PedidoLoteRubrica(AcaoLoteRubrica acao, List<UUID> linhas) {
    }

    public record LinhaIgnorada(UUID linhaId, String motivo) {
    }

    public record ResultadoLoteRubrica(int alteradas, List<LinhaIgnorada> ignoradas) {
    }

    public record LinhaSemSugestao(UUID linhaId, String codigo, String conta, String motivo) {
    }

    /**
     * {@code primeiraPo}: o condomínio não tinha rubricas, e cada linha desta PO virou uma rubrica já confirmada.
     */
    public record ResultadoSugestoesRubrica(boolean primeiraPo, int rubricasCriadas, int sugeridas,
            int daVersaoAnterior, int pelaConta, List<LinhaSemSugestao> semSugestao) {
    }

    public record EventoRubricaDto(UUID id, UUID linhaId, String codigo, String descricao, EventoRubrica.Acao acao,
            String usuario, Instant em, String rubricaAnterior, EstadoRubrica estadoAnterior, String rubricaNova,
            EstadoRubrica estadoNovo, OrigemRubrica origem, String motivo) {

        static EventoRubricaDto de(EventoRubrica e) {
            return new EventoRubricaDto(e.getId(), e.getLinhaPoId(), e.getLinhaCodigo(), e.getLinhaDescricao(),
                    e.getAcao(), e.getUsuario(), e.getEm(), e.getRubricaAnterior(), e.getEstadoAnterior(),
                    e.getRubricaNova(), e.getEstadoNovo(), e.getOrigem(), e.getMotivo());
        }
    }
}
