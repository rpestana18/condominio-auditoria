package br.com.condominioauditoria.api.orcamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Entrada e saída da API de realocação (RF-03.1.7). */
public final class RealocacaoDtos {

    private RealocacaoDtos() {
    }

    /** Lançamento (id da evidência "a realocar") e a linha da PO de destino. */
    public record PedidoRealocacao(UUID lancamentoId, UUID linhaId) {
    }

    /** Realocação com a impressão do lançamento original (que continua intacto no fluxo). */
    public record RealocacaoDto(UUID id, UUID previsaoId, LocalDate data, String conta, String contaNome,
            String documento, String historico, BigDecimal valor, UUID arquivoId, String sha256, int pagina, int ordem,
            UUID linhaId, String linhaCodigo, String linhaDescricao, String realocadaPor, Instant realocadaEm,
            String desfeitaPor, Instant desfeitaEm, boolean ativa) {

        static RealocacaoDto de(RealocacaoLancamento r, LinhaPo linha) {
            return new RealocacaoDto(r.getId(), r.getPrevisaoId(), r.getData(), r.getContaCodigo(), r.getContaNome(),
                    r.getDocumento(), r.getHistorico(), r.getValor(), r.getArquivoId(), r.getSha256(), r.getPagina(),
                    r.getOrdem(), r.getLinhaPoId(), linha == null ? null : linha.getCodigoEfetivo(),
                    linha == null ? null : linha.getDescricao(), r.getRealocadaPor(), r.getRealocadaEm(),
                    r.getDesfeitaPor(), r.getDesfeitaEm(), r.ativa());
        }
    }
}
