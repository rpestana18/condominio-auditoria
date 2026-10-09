package br.com.condominioauditoria.api.repository.file;

import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.FileStatus;
import br.com.condominioauditoria.api.model.enums.IndexingStatus;
import br.com.condominioauditoria.api.model.file.SourceFile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceFileRepository extends JpaRepository<SourceFile, UUID> {

    List<SourceFile> findByCondominiumIdOrderByUploadedAtDesc(UUID condominiumId);

    List<SourceFile> findByCondominiumIdAndCategoryOrderByUploadedAtDesc(UUID condominiumId, FileCategory category);

    Optional<SourceFile> findFirstByCondominiumIdOrderByUploadedAtDesc(UUID condominiumId);

    Optional<SourceFile> findByCondominiumIdAndSha256(UUID condominiumId, String sha256);

    Optional<SourceFile> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    /** Files read in a category (e.g. completed cash flows, for budget vs. actual). */
    List<SourceFile> findByCondominiumIdAndCategoryAndStatusIn(UUID condominiumId, FileCategory category,
            Collection<FileStatus> status);

    List<SourceFile> findByStatusInOrderByUploadedAt(Collection<FileStatus> status);

    /** Stalled in the queue for longer than the cutoff (lost message, service down). */
    List<SourceFile> findByStatusInAndQueuedAtBeforeOrderByUploadedAt(Collection<FileStatus> status, java.time.Instant cutoff);

    /** Indexing requests stalled for longer than the cutoff (lost message, rag down). */
    List<SourceFile> findByIndexingStatusInAndIndexingQueuedAtBeforeOrderByUploadedAt(
            Collection<IndexingStatus> statuses, java.time.Instant cutoff);

    /**
     * Of the given ids, only those that exist and belong to the condominium (second barrier of the document search).
     */
    List<SourceFile> findByCondominiumIdAndIdIn(UUID condominiumId, Collection<UUID> ids);

    /** Latest cash flow read (with or without a pending review), by the most recent period. */
    Optional<SourceFile> findFirstByCondominiumIdAndParserAndStatusInOrderByPeriodEndDescUploadedAtDesc(
            UUID condominiumId, String parser, Collection<FileStatus> status);
}
