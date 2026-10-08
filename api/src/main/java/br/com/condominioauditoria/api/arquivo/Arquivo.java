package br.com.condominioauditoria.api.arquivo;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;

/** Registro de um arquivo enviado. O conteúdo fica na pasta de dados; aqui só o caminho, o hash e o status. */
// Só as colunas alteradas vão no UPDATE: a gravação do resultado da leitura e a da indexação chegam por filas
// diferentes e mexem em colunas diferentes; sem isto, a transação mais longa regravava a linha inteira e apagava a
// situação da indexação gravada no meio-tempo (arquivo indexado voltava a aparecer "na fila").
@DynamicUpdate
@Entity
public class Arquivo {

    @Id
    private UUID id;
    private UUID condominioId;
    @Enumerated(EnumType.STRING)
    private Categoria categoria;
    private String nomeOriginal;
    private String caminho;
    private String sha256;
    private long tamanhoBytes;
    private String tipoConteudo;
    @Enumerated(EnumType.STRING)
    private StatusArquivo status;
    private String mensagem;
    private String interpretador;
    private LocalDate periodoInicio;
    private LocalDate periodoFim;
    private Integer totalLancamentos;
    private String enviadoPor;
    private Instant enviadoEm;
    private Instant processadoEm;
    /** Identifica a leitura em andamento. Resultado que chega com outro id (velho ou repetido) é descartado. */
    private UUID processamentoId;
    /** Quando foi colocado na fila pela última vez; a varredura reenvia o que ficou parado. */
    private Instant enfileiradoEm;
    private int tentativas;

    // Indexação para a busca nos documentos (ADR 0003). Tudo nulo = arquivo ainda não pedido ao índice.
    @Enumerated(EnumType.STRING)
    private SituacaoIndexacao indexacaoSituacao;
    private String indexacaoMotivo;
    private Integer indexacaoPaginas;
    private Integer indexacaoTrechos;
    /** Identifica o pedido de indexação em andamento. Resultado com outro id (velho ou repetido) é descartado. */
    private UUID indexacaoId;
    /** Quando o pedido de indexação foi colocado na fila pela última vez; a varredura reenvia o que ficou parado. */
    private Instant indexacaoEnfileiradaEm;
    private int indexacaoTentativas;
    private Instant indexacaoAtualizadaEm;

    protected Arquivo() {
    }

    public Arquivo(UUID condominioId, Categoria categoria, String nomeOriginal, String caminho, String sha256,
            long tamanhoBytes, String tipoConteudo, String enviadoPor) {
        this.id = UUID.randomUUID();
        this.condominioId = condominioId;
        this.categoria = categoria;
        this.nomeOriginal = nomeOriginal;
        this.caminho = caminho;
        this.sha256 = sha256;
        this.tamanhoBytes = tamanhoBytes;
        this.tipoConteudo = tipoConteudo;
        this.enviadoPor = enviadoPor;
        this.enviadoEm = Instant.now();
        this.status = StatusArquivo.PENDENTE;
        this.processamentoId = UUID.randomUUID();
        this.enfileiradoEm = this.enviadoEm;
        this.tentativas = 1;
        // Sem indexação: quem grava decide pedir (só com o módulo Assistente ligado, RF-10.3)
    }

    /** Nova leitura do zero (reprocessar): resultados de leituras anteriores passam a ser ignorados. */
    public void novoProcessamento() {
        status = StatusArquivo.PENDENTE;
        mensagem = null;
        processamentoId = UUID.randomUUID();
        enfileiradoEm = Instant.now();
        tentativas = 1;
    }

    /** Troca a categoria. O original na pasta não muda: o caminho continua o mesmo do envio. */
    public void trocarCategoria(Categoria nova) {
        this.categoria = nova;
    }

    /** Reenvio da mesma leitura (mensagem perdida ou serviço reiniciado). */
    public void reenviar() {
        enfileiradoEm = Instant.now();
        tentativas++;
    }

    public boolean ehDoProcessamento(UUID id) {
        return processamentoId != null && processamentoId.equals(id);
    }

    /** Novo pedido de indexação (envio, reprocesso, reindexar): resultados de pedidos anteriores passam a ser ignorados. */
    public void novaIndexacao() {
        indexacaoSituacao = SituacaoIndexacao.NA_FILA;
        indexacaoMotivo = null;
        indexacaoPaginas = null;
        indexacaoTrechos = null;
        indexacaoId = UUID.randomUUID();
        indexacaoEnfileiradaEm = Instant.now();
        indexacaoTentativas = 1;
        indexacaoAtualizadaEm = indexacaoEnfileiradaEm;
    }

    /** Reenvio do mesmo pedido de indexação (mensagem perdida ou serviço reiniciado). O rag é idempotente. */
    public void reenviarIndexacao() {
        indexacaoEnfileiradaEm = Instant.now();
        indexacaoTentativas++;
    }

    public boolean ehDaIndexacao(UUID id) {
        return indexacaoId != null && indexacaoId.equals(id);
    }

    /** O rag começou. Só sai de Na fila: um "indexando" atrasado não desfaz um resultado que já chegou. */
    public void iniciarIndexacao() {
        if (indexacaoSituacao == SituacaoIndexacao.NA_FILA) {
            indexacaoSituacao = SituacaoIndexacao.INDEXANDO;
            indexacaoAtualizadaEm = Instant.now();
        }
    }

    /** Resultado final da indexação (Indexado, Sem texto, Retirado ou Erro), com o que o rag informou. */
    public void concluirIndexacao(SituacaoIndexacao situacao, String motivo, Integer paginas, Integer trechos) {
        if (situacao == SituacaoIndexacao.NA_FILA || situacao == SituacaoIndexacao.INDEXANDO) {
            throw new IllegalArgumentException("Situação não é final: " + situacao);
        }
        indexacaoSituacao = situacao;
        indexacaoMotivo = motivo;
        indexacaoPaginas = paginas;
        indexacaoTrechos = trechos;
        indexacaoAtualizadaEm = Instant.now();
    }

    public void falharIndexacao(String motivo) {
        concluirIndexacao(SituacaoIndexacao.ERRO, motivo, null, null);
    }

    public void iniciarProcessamento() {
        status = StatusArquivo.PROCESSANDO;
        mensagem = null;
    }

    public void concluir(StatusArquivo resultado, String mensagem, String interpretador, LocalDate inicio, LocalDate fim,
            Integer totalLancamentos) {
        this.status = resultado;
        this.mensagem = mensagem;
        this.interpretador = interpretador;
        this.periodoInicio = inicio;
        this.periodoFim = fim;
        this.totalLancamentos = totalLancamentos;
        this.processadoEm = Instant.now();
    }

    public void falhar(String motivo) {
        status = StatusArquivo.FALHOU;
        mensagem = motivo;
        processadoEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominioId() {
        return condominioId;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public String getNomeOriginal() {
        return nomeOriginal;
    }

    public String getCaminho() {
        return caminho;
    }

    public String getSha256() {
        return sha256;
    }

    public long getTamanhoBytes() {
        return tamanhoBytes;
    }

    public String getTipoConteudo() {
        return tipoConteudo;
    }

    public StatusArquivo getStatus() {
        return status;
    }

    public String getMensagem() {
        return mensagem;
    }

    public String getInterpretador() {
        return interpretador;
    }

    public LocalDate getPeriodoInicio() {
        return periodoInicio;
    }

    public LocalDate getPeriodoFim() {
        return periodoFim;
    }

    public Integer getTotalLancamentos() {
        return totalLancamentos;
    }

    public String getEnviadoPor() {
        return enviadoPor;
    }

    public Instant getEnviadoEm() {
        return enviadoEm;
    }

    public Instant getProcessadoEm() {
        return processadoEm;
    }

    public UUID getProcessamentoId() {
        return processamentoId;
    }

    public Instant getEnfileiradoEm() {
        return enfileiradoEm;
    }

    public int getTentativas() {
        return tentativas;
    }

    public SituacaoIndexacao getIndexacaoSituacao() {
        return indexacaoSituacao;
    }

    public String getIndexacaoMotivo() {
        return indexacaoMotivo;
    }

    public Integer getIndexacaoPaginas() {
        return indexacaoPaginas;
    }

    public Integer getIndexacaoTrechos() {
        return indexacaoTrechos;
    }

    public UUID getIndexacaoId() {
        return indexacaoId;
    }

    public Instant getIndexacaoEnfileiradaEm() {
        return indexacaoEnfileiradaEm;
    }

    public int getIndexacaoTentativas() {
        return indexacaoTentativas;
    }

    public Instant getIndexacaoAtualizadaEm() {
        return indexacaoAtualizadaEm;
    }
}
