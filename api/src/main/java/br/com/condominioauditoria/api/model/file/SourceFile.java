package br.com.condominioauditoria.api.model.file;

import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.FileStatus;
import br.com.condominioauditoria.api.model.enums.IndexingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;

/** Record of an uploaded file. The content stays in the data folder; here only the path, the hash and the status. */
// Only the changed columns go into the UPDATE: saving the read result and saving the indexing result arrive through
// different queues and touch different columns; without this, the longer transaction rewrote the whole row and erased
// the indexing status saved in the meantime (an indexed file showed up as "queued" again).
@DynamicUpdate
@Entity
@Table(name = "arquivo")
public class SourceFile {

    @Id
    private UUID id;
    @Column(name = "condominio_id")
    private UUID condominiumId;
    @Column(name = "categoria")
    @Enumerated(EnumType.STRING)
    private FileCategory category;
    @Column(name = "nome_original")
    private String originalName;
    @Column(name = "caminho")
    private String path;
    private String sha256;
    @Column(name = "tamanho_bytes")
    private long sizeBytes;
    @Column(name = "tipo_conteudo")
    private String contentType;
    @Enumerated(EnumType.STRING)
    private FileStatus status;
    @Column(name = "mensagem")
    private String message;
    @Column(name = "interpretador")
    private String parser;
    @Column(name = "periodo_inicio")
    private LocalDate periodStart;
    @Column(name = "periodo_fim")
    private LocalDate periodEnd;
    @Column(name = "total_lancamentos")
    private Integer entryCount;
    @Column(name = "enviado_por")
    private String uploadedBy;
    @Column(name = "enviado_em")
    private Instant uploadedAt;
    @Column(name = "processado_em")
    private Instant processedAt;
    /** Identifies the read in progress. A result that arrives with another id (old or repeated) is discarded. */
    @Column(name = "processamento_id")
    private UUID processingId;
    /** When it was last queued; the sweep resends what stalled. */
    @Column(name = "enfileirado_em")
    private Instant queuedAt;
    @Column(name = "tentativas")
    private int attempts;

    // Indexing for the document search (ADR 0003). All null = the file was never sent to the index.
    @Column(name = "indexacao_situacao")
    @Enumerated(EnumType.STRING)
    private IndexingStatus indexingStatus;
    @Column(name = "indexacao_motivo")
    private String indexingReason;
    @Column(name = "indexacao_paginas")
    private Integer indexingPages;
    @Column(name = "indexacao_trechos")
    private Integer indexingChunks;
    /** Identifies the indexing request in progress. A result with another id (old or repeated) is discarded. */
    @Column(name = "indexacao_id")
    private UUID indexingId;
    /** When the indexing request was last queued; the sweep resends what stalled. */
    @Column(name = "indexacao_enfileirada_em")
    private Instant indexingQueuedAt;
    @Column(name = "indexacao_tentativas")
    private int indexingAttempts;
    @Column(name = "indexacao_atualizada_em")
    private Instant indexingUpdatedAt;

    protected SourceFile() {
    }

    public SourceFile(UUID condominiumId, FileCategory category, String originalName, String path, String sha256,
            long sizeBytes, String contentType, String uploadedBy) {
        this.id = UUID.randomUUID();
        this.condominiumId = condominiumId;
        this.category = category;
        this.originalName = originalName;
        this.path = path;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
        this.contentType = contentType;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = Instant.now();
        this.status = FileStatus.PENDENTE;
        this.processingId = UUID.randomUUID();
        this.queuedAt = this.uploadedAt;
        this.attempts = 1;
        // No indexing: whoever saves decides to request it (only with the Assistant feature enabled, RF-10.3)
    }

    /** New read from scratch (reprocess): results of previous reads are ignored from now on. */
    public void requestProcessing() {
        status = FileStatus.PENDENTE;
        message = null;
        processingId = UUID.randomUUID();
        queuedAt = Instant.now();
        attempts = 1;
    }

    /** Changes the category. The original in the folder does not change: the path stays the same as on upload. */
    public void changeCategory(FileCategory newCategory) {
        this.category = newCategory;
    }

    /** Resend of the same read (lost message or restarted service). */
    public void resend() {
        queuedAt = Instant.now();
        attempts++;
    }

    public boolean isCurrentProcessing(UUID id) {
        return processingId != null && processingId.equals(id);
    }

    /** New indexing request (upload, reprocess, reindex): results of previous requests are ignored from now on. */
    public void requestIndexing() {
        indexingStatus = IndexingStatus.NA_FILA;
        indexingReason = null;
        indexingPages = null;
        indexingChunks = null;
        indexingId = UUID.randomUUID();
        indexingQueuedAt = Instant.now();
        indexingAttempts = 1;
        indexingUpdatedAt = indexingQueuedAt;
    }

    /** Resend of the same indexing request (lost message or restarted service). The rag is idempotent. */
    public void resendIndexing() {
        indexingQueuedAt = Instant.now();
        indexingAttempts++;
    }

    public boolean isCurrentIndexing(UUID id) {
        return indexingId != null && indexingId.equals(id);
    }

    /** The rag started. Only leaves Queued: a late "indexing" does not undo a result that already arrived. */
    public void startIndexing() {
        if (indexingStatus == IndexingStatus.NA_FILA) {
            indexingStatus = IndexingStatus.INDEXANDO;
            indexingUpdatedAt = Instant.now();
        }
    }

    /** Final indexing result (Indexed, No text, Withdrawn or Error), with what the rag reported. */
    public void completeIndexing(IndexingStatus status, String reason, Integer pages, Integer chunks) {
        if (status == IndexingStatus.NA_FILA || status == IndexingStatus.INDEXANDO) {
            throw new IllegalArgumentException("Situação não é final: " + status);
        }
        indexingStatus = status;
        indexingReason = reason;
        indexingPages = pages;
        indexingChunks = chunks;
        indexingUpdatedAt = Instant.now();
    }

    public void failIndexing(String reason) {
        completeIndexing(IndexingStatus.ERRO, reason, null, null);
    }

    public void startProcessing() {
        status = FileStatus.PROCESSANDO;
        message = null;
    }

    public void complete(FileStatus result, String message, String parser, LocalDate start, LocalDate end,
            Integer entryCount) {
        this.status = result;
        this.message = message;
        this.parser = parser;
        this.periodStart = start;
        this.periodEnd = end;
        this.entryCount = entryCount;
        this.processedAt = Instant.now();
    }

    public void fail(String reason) {
        status = FileStatus.FALHOU;
        message = reason;
        processedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public FileCategory getCategory() {
        return category;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getPath() {
        return path;
    }

    public String getSha256() {
        return sha256;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getContentType() {
        return contentType;
    }

    public FileStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getParser() {
        return parser;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public Integer getEntryCount() {
        return entryCount;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public UUID getProcessingId() {
        return processingId;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public IndexingStatus getIndexingStatus() {
        return indexingStatus;
    }

    public String getIndexingReason() {
        return indexingReason;
    }

    public Integer getIndexingPages() {
        return indexingPages;
    }

    public Integer getIndexingChunks() {
        return indexingChunks;
    }

    public UUID getIndexingId() {
        return indexingId;
    }

    public Instant getIndexingQueuedAt() {
        return indexingQueuedAt;
    }

    public int getIndexingAttempts() {
        return indexingAttempts;
    }

    public Instant getIndexingUpdatedAt() {
        return indexingUpdatedAt;
    }
}
