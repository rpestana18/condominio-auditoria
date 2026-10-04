package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.orcamento.AvaliacaoLeituraPo.Classificacao;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Respostas da API da PO (contrato em contracts/openapi.yaml). Meses no formato AAAA-MM. */
public final class PrevisaoDtos {

    private PrevisaoDtos() {
    }

    public record PrevisaoResumo(UUID id, UUID arquivoId, String arquivoNome, String sha256, EstadoPrevisao estado,
            Integer versao, String titulo, String exercicioImpresso, String exercicioInicio, String exercicioFim,
            BigDecimal totalImpresso, BigDecimal previstoMes, Instant lidaEm, String confirmadaPor,
            Instant confirmadaEm) {
    }

    public record PrevisaoDetalhe(PrevisaoResumo previsao, String colunaOrcadoAnterior, String colunaOrcado,
            BigDecimal previstoMesImpresso, BigDecimal toleranciaArredondamento, String substituidaDesde,
            Confirmacao confirmacao, List<LinhaPoDto> linhas, List<ConferenciaPoDto> conferencias,
            List<AvisoDto> avisos, List<CodigoRepetidoDto> codigosRepetidos, List<FundoPoDto> fundos,
            List<AchadoDto> achados) {
    }

    /** Dados informados pelo Admin na confirmação; nulo antes dela. */
    public record Confirmacao(UUID ataArquivoId, boolean semAta, java.time.LocalDate dataAprovacao,
            boolean cienteDivergencia, String justificativaDivergencia) {
    }

    public record LinhaPoDto(UUID id, int ordem, int pagina, TipoLinhaPo tipo, String codigoImpresso,
            String codigoEfetivo, String conta, String contaTexto, MarcaPo marca, String descricao,
            BigDecimal orcadoAnterior, BigDecimal orcado, String percentualTexto, String observacoes,
            boolean linhaDeFundo, UUID arquivoId, String sha256) {

        static LinhaPoDto de(LinhaPo l, boolean linhaDeFundo) {
            return new LinhaPoDto(l.getId(), l.getOrdem(), l.getPagina(), l.getTipo(), l.getCodigoImpresso(),
                    l.getCodigoEfetivo(), l.getConta(), l.getContaTexto(), l.getMarca(), l.getDescricao(),
                    l.getOrcadoAnterior(), l.getOrcado(), l.getPercentualTexto(), l.getObservacoes(), linhaDeFundo,
                    l.getArquivoId(), l.getSha256());
        }
    }

    public record ConferenciaPoDto(String codigo, String descricao, boolean ok, String detalhe,
            Classificacao classificacao, String explicacao) {
    }

    public enum CodigoAviso {
        ARREDONDAMENTO, DIVERGENCIA, CODIGO_REPETIDO, CONFIRMADA_COM_DIVERGENCIA, FORA_PRIMEIRO_TRIMESTRE, SEM_ATA,
        REGRA_NAO_AVALIADA
    }

    public record AvisoDto(CodigoAviso codigo, String texto) {
    }

    public record CodigoRepetidoDto(String codigoImpresso, boolean resolvido, List<LinhaRepetidaDto> linhas) {
    }

    public record LinhaRepetidaDto(UUID linhaId, int ordem, String descricao, String codigoEfetivo) {
    }

    public record FundoPoDto(UUID linhaId, String codigoEfetivo, String descricao, BigDecimal orcado, UUID fundoId,
            String fundo) {
    }

    public record AchadoDto(UUID id, String regra, String versaoRegra, String severidade, String competencia,
            String descricao, String estado) {
    }

    /** Trilha da PO: confirmação, substituição e alteração da ligação dos fundos. */
    public record EventoPrevisaoDto(String tipo, String usuario, java.time.Instant em, String justificativa,
            String detalhe) {

        static EventoPrevisaoDto de(EventoPrevisao e) {
            return new EventoPrevisaoDto(e.getTipo(), e.getUsuario(), e.getEm(), e.getJustificativa(), e.getDetalhe());
        }
    }
}
