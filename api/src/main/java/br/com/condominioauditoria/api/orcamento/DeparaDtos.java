package br.com.condominioauditoria.api.orcamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Pedidos e respostas da API do de-para (contrato em contracts/openapi.yaml). */
public final class DeparaDtos {

    private DeparaDtos() {
    }

    /** Filtros da tela "De-para" (RF-03.1.13). PENDENTES = sem de-para confirmado (sem, sugerido ou recusado). */
    public enum FiltroDepara {
        TODAS, PENDENTES, SUGERIDO, CONFIRMADO, RECUSADO, SEM_DEPARA, IGUAIS_VERSAO_ANTERIOR
    }

    public record DestinoDto(TipoDestino tipo, UUID linhaId, String codigo, String descricao, String detalhe,
            String texto) {

        static DestinoDto de(Destino d) {
            return d == null ? null : new DestinoDto(d.tipo(), d.linhaPoId(), d.codigo(), d.descricao(), d.detalhe(),
                    d.texto());
        }
    }

    /**
     * Uma conta do fluxo. {@code estado} nulo: conta sem de-para nesta versão. {@code lancamentos} e {@code debitos}:
     * débitos do fundo Condomínio no exercício da PO (zero quando a conta só veio da planilha).
     */
    public record ContaDepara(String conta, String nome, int lancamentos, BigDecimal debitos, DestinoDto destino,
            EstadoDepara estado, OrigemDepara origem, String motivo, boolean igualVersaoAnterior, String atualizadoPor,
            Instant atualizadoEm) {
    }

    public record ResumoDepara(int contas, int confirmadas, int sugeridas, int recusadas, int semDepara) {
    }

    public record DeparaLista(UUID previsaoId, Integer versao, ResumoDepara resumo, List<ContaDepara> contas) {
    }

    /** Destino escolhido pelo Admin. {@code confirmar} nulo = true (escolha na lista já confirma). */
    public record PedidoDestino(TipoDestino tipo, UUID linhaId, String detalhe, Boolean confirmar) {
    }

    public enum AcaoLote {
        CONFIRMAR, RECUSAR
    }

    public record PedidoLote(AcaoLote acao, List<String> contas) {
    }

    public record ContaIgnorada(String conta, String motivo) {
    }

    public record ResultadoLote(int alteradas, List<ContaIgnorada> ignoradas) {
    }

    public record ContaSemSugestao(String conta, String nome, String motivo) {
    }

    public record ResultadoSugestoes(int criadas, int daVersaoAnterior, int peloNome, List<ContaSemSugestao> semSugestao) {
    }

    public record LinhaRecusada(int linha, String conteudo, String motivo) {
    }

    public record ResultadoPlanilha(int aceitas, List<ContaIgnorada> ignoradas, List<LinhaRecusada> recusadas) {
    }

    public record EventoDeparaDto(UUID id, String conta, String nome, EventoDepara.Acao acao, String usuario, Instant em,
            String destinoAnterior, EstadoDepara estadoAnterior, String destinoNovo, EstadoDepara estadoNovo,
            OrigemDepara origem, String motivo) {

        static EventoDeparaDto de(EventoDepara e) {
            return new EventoDeparaDto(e.getId(), e.getContaCodigo(), e.getContaNome(), e.getAcao(), e.getUsuario(),
                    e.getEm(), e.getDestinoAnterior(), e.getEstadoAnterior(), e.getDestinoNovo(), e.getEstadoNovo(),
                    e.getOrigem(), e.getMotivo());
        }
    }
}
