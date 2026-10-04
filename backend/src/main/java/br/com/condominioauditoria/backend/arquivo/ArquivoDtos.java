package br.com.condominioauditoria.backend.arquivo;

import br.com.condominioauditoria.backend.contabil.Conferencia;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Respostas da API de arquivos (contrato em contracts/openapi.yaml). */
final class ArquivoDtos {

    private ArquivoDtos() {
    }

    record ArquivoResumo(UUID id, Categoria categoria, String categoriaRotulo, String nome, long tamanhoBytes,
            StatusArquivo status, String mensagem, LocalDate periodoInicio, LocalDate periodoFim,
            Integer totalLancamentos, String enviadoPor, Instant enviadoEm, Instant processadoEm,
            IndexacaoDto indexacao) {

        static ArquivoResumo de(Arquivo a) {
            return new ArquivoResumo(a.getId(), a.getCategoria(), a.getCategoria().rotulo(), a.getNomeOriginal(),
                    a.getTamanhoBytes(), a.getStatus(), a.getMensagem(), a.getPeriodoInicio(), a.getPeriodoFim(),
                    a.getTotalLancamentos(), a.getEnviadoPor(), a.getEnviadoEm(), a.getProcessadoEm(),
                    IndexacaoDto.de(a));
        }
    }

    /** Estado da indexação para a busca nos documentos. Nulo = arquivo ainda não pedido ao índice. */
    record IndexacaoDto(SituacaoIndexacao situacao, String motivo, Integer paginas, Integer trechos) {

        static IndexacaoDto de(Arquivo a) {
            return a.getIndexacaoSituacao() == null ? null
                    : new IndexacaoDto(a.getIndexacaoSituacao(), a.getIndexacaoMotivo(), a.getIndexacaoPaginas(),
                            a.getIndexacaoTrechos());
        }
    }

    record ArquivoDetalhe(ArquivoResumo arquivo, String sha256, List<ConferenciaDto> conferencias, List<SaldoDto> fundos) {
    }

    record ConferenciaDto(String codigo, String descricao, boolean ok, String detalhe) {
        static ConferenciaDto de(Conferencia c) {
            return new ConferenciaDto(c.getCodigo(), c.getDescricao(), c.isOk(), c.getDetalhe());
        }
    }

    record SaldoDto(String fundo, BigDecimal saldoAnterior, BigDecimal creditos, BigDecimal debitos, BigDecimal saldoAtual) {
    }

    record CategoriaDto(Categoria codigo, String rotulo) {
    }

    /** Pedido de troca de categoria (RF-01.7). */
    record NovaCategoria(Categoria categoria) {
    }
}
