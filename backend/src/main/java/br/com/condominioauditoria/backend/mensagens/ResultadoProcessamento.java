package br.com.condominioauditoria.backend.mensagens;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Mensagem rag → backend. Contrato: contracts/mensagens/v2/resultado-processamento.schema.json.
 * Estes registros são do backend: o rag tem os dele. Só o contrato JSON é comum aos dois.
 *
 * {@code previsaoOrcamentaria} é lida e conferida pelo contrato, mas ainda não é gravada (ADR 0004, passo 4).
 */
public record ResultadoProcessamento(int versao, UUID processamentoId, UUID arquivoId, UUID condominioId,
        Situacao situacao, String motivo, String interpretador, Integer paginas, Fluxo fluxoDeCaixa,
        PrevisaoLida previsaoOrcamentaria, List<ConferenciaLida> conferencias) {

    public enum Situacao {
        INICIADO, CONCLUIDO, FALHOU
    }

    public record Fluxo(String empreendimento, LocalDate periodoInicio, LocalDate periodoFim, List<Secao> secoes,
            List<Posicao> posicaoFinanceira, Posicao totalPosicao) {

        public int totalLancamentos() {
            return secoes.stream().mapToInt(s -> s.lancamentos().size()).sum();
        }
    }

    public record Secao(String fundo, BigDecimal saldoAnterior, List<LancamentoLido> lancamentos,
            BigDecimal totalCreditosInformado, BigDecimal totalDebitosInformado) {
    }

    public record LancamentoLido(int pagina, int ordem, LocalDate data, String contaCodigo, String contaNome,
            String documento, String historico, BigDecimal credito, BigDecimal debito, BigDecimal saldo,
            Enriquecimento enriquecimento) {
    }

    public record Enriquecimento(String notaFiscal, String fornecedor, String meioPagamento,
            boolean transferenciaEntreFundos, boolean recebimentoCota) {
    }

    /** PO lida, como está no documento. {@code colunasOrcado} vem na ordem [anterior, exercício]. */
    public record PrevisaoLida(String titulo, String exercicioImpresso, List<String> colunasOrcado,
            List<LinhaPoLida> linhas) {
    }

    public enum TipoLinhaPo {
        TOTAL, GRUPO, LINHA
    }

    public enum MarcaPo {
        RATEIO_A_PARTE, NEGOCIADA_ISENCAO, SEM_VALOR, VALOR_FIXO_SEM_REFERENCIA
    }

    /** Uma linha da PO. {@code ordem} distingue linhas com o mesmo código impresso. "%" e Observações são texto. */
    public record LinhaPoLida(int ordem, int pagina, TipoLinhaPo tipo, String codigoImpresso, String conta,
            String contaTexto, MarcaPo marca, String descricao, BigDecimal orcadoAnterior, BigDecimal orcado,
            String percentualTexto, String observacoes) {
    }

    public record Posicao(String fundo, BigDecimal saldoAnterior, BigDecimal creditos, BigDecimal debitos,
            BigDecimal saldoAtual) {
    }

    public record ConferenciaLida(String codigo, String descricao, boolean ok, String detalhe) {
    }
}
