package br.com.condominioauditoria.api.service.file;

import br.com.condominioauditoria.api.dto.response.file.FileContentResponse;
import br.com.condominioauditoria.api.dto.response.file.SourceFileDetailResponse;
import br.com.condominioauditoria.api.dto.response.file.SourceFileResponse;
import br.com.condominioauditoria.api.event.FileIndexRequested;
import br.com.condominioauditoria.api.event.FileReadRequested;
import br.com.condominioauditoria.api.exception.DuplicateFileException;
import br.com.condominioauditoria.api.mapper.SourceFileMapper;
import br.com.condominioauditoria.api.model.accounting.Fund;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.FileStatus;
import br.com.condominioauditoria.api.model.file.CategoryChange;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.accounting.FundBalanceRepository;
import br.com.condominioauditoria.api.repository.accounting.FundRepository;
import br.com.condominioauditoria.api.repository.accounting.TotalsCheckRepository;
import br.com.condominioauditoria.api.repository.file.CategoryChangeRepository;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import br.com.condominioauditoria.storage.Storage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Year;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Receives files: stores the original in the data folder, records it in the database and, after the commit, asks the
 * rag through the queue to read it and to index it for the document search (separate queues, ADR 0003, Decision 5.1).
 * Indexing belongs to the Assistant feature: with it disabled for the condominium, the file only goes through the core
 * and its indexing status stays as it is (null on a new file), with no message at all (RF-10.3).
 */
@Service
public class SourceFileService {

    private final SourceFileRepository files;
    private final Storage storage;
    private final ApplicationEventPublisher events;
    private final CategoryChangeRepository categoryChanges;
    private final FeatureService features;
    private final TotalsCheckRepository totalsChecks;
    private final FundBalanceRepository balances;
    private final FundRepository funds;

    public SourceFileService(SourceFileRepository files, Storage storage, ApplicationEventPublisher events,
            CategoryChangeRepository categoryChanges, FeatureService features, TotalsCheckRepository totalsChecks,
            FundBalanceRepository balances, FundRepository funds) {
        this.files = files;
        this.storage = storage;
        this.events = events;
        this.categoryChanges = categoryChanges;
        this.features = features;
        this.totalsChecks = totalsChecks;
        this.balances = balances;
        this.funds = funds;
    }

    /** Newest first, optionally filtered by category. */
    @Transactional(readOnly = true)
    public List<SourceFileResponse> list(UUID condominiumId, FileCategory category) {
        var list = category == null
                ? files.findByCondominiumIdOrderByUploadedAtDesc(condominiumId)
                : files.findByCondominiumIdAndCategoryOrderByUploadedAtDesc(condominiumId, category);
        return list.stream().map(SourceFileMapper::toResponse).toList();
    }

    /** For the discreet "latest file" indicator in the corner of the screen. */
    @Transactional(readOnly = true)
    public Optional<SourceFileResponse> latest(UUID condominiumId) {
        return files.findFirstByCondominiumIdOrderByUploadedAtDesc(condominiumId).map(SourceFileMapper::toResponse);
    }

    /** The file with the totals checks and the fund balances read from it. */
    @Transactional(readOnly = true)
    public SourceFileDetailResponse detail(UUID condominiumId, UUID id) {
        SourceFile file = find(condominiumId, id);
        Map<UUID, String> fundNames = funds.findByCondominiumId(condominiumId).stream()
                .collect(Collectors.toMap(Fund::getId, Fund::getName));
        return new SourceFileDetailResponse(SourceFileMapper.toResponse(file), file.getSha256(),
                totalsChecks.findByFileIdOrderBySequence(id).stream().map(SourceFileMapper::toResponse).toList(),
                balances.findByFileId(id).stream()
                        .map(b -> SourceFileMapper.toResponse(b, fundNames.get(b.getFundId())))
                        .toList());
    }

    /** Opens the original, exactly as it was uploaded. The caller closes the stream. */
    @Transactional(readOnly = true)
    public FileContentResponse content(UUID condominiumId, UUID id) throws IOException {
        SourceFile file = find(condominiumId, id);
        return new FileContentResponse(file.getOriginalName(), file.getContentType(), storage.open(file.getPath()));
    }

    @Transactional
    public SourceFileResponse upload(UUID condominiumId, FileCategory category, MultipartFile upload, String username)
            throws IOException {
        return SourceFileMapper.toResponse(receive(condominiumId, category, upload, username));
    }

    /**
     * Reprocessing is safe: saving the result deletes the file's previous extraction before inserting the new one. With
     * the Assistant feature enabled it also reindexes (RF-04.6); the rag does not redo what has not changed.
     */
    @Transactional
    public SourceFileResponse reprocess(UUID condominiumId, UUID id) {
        return SourceFileMapper.toResponse(reprocess(find(condominiumId, id)));
    }

    /**
     * Changes the category and reprocesses (RF-01.7): saving the result deletes what was extracted under the old
     * category. The same category does nothing. The change is recorded with who made it, when, the previous and the
     * new category.
     */
    @Transactional
    public SourceFileResponse changeCategory(UUID condominiumId, UUID id, FileCategory newCategory, String username) {
        SourceFile file = find(condominiumId, id);
        if (file.getCategory() == newCategory) {
            return SourceFileMapper.toResponse(file);
        }
        if (file.getStatus() == FileStatus.PROCESSING) {
            throw new IllegalStateException("O arquivo já está sendo processado");
        }
        categoryChanges.save(new CategoryChange(file.getId(), file.getCategory(), newCategory, username));
        file.changeCategory(newCategory);
        return SourceFileMapper.toResponse(reprocess(file));
    }

    private SourceFile receive(UUID condominiumId, FileCategory category, MultipartFile upload, String username)
            throws IOException {
        Path tempFile = Files.createTempFile("envio-", ".bin");
        try {
            String sha256;
            try (InputStream input = upload.getInputStream()) {
                sha256 = copyComputingHash(input, tempFile);
            }
            var existing = files.findByCondominiumIdAndSha256(condominiumId, sha256);
            if (existing.isPresent()) {
                throw new DuplicateFileException(existing.get());
            }
            String name = safeName(upload.getOriginalFilename());
            String path = "%s/%s/%d/%s-%s".formatted(condominiumId, category, Year.now().getValue(),
                    sha256.substring(0, 12), name);
            if (!storage.exists(path)) {
                try (InputStream input = Files.newInputStream(tempFile)) {
                    storage.store(path, input);
                }
            }
            var newFile = new SourceFile(condominiumId, category, name, path, sha256, Files.size(tempFile),
                    upload.getContentType(), username);
            boolean indexingRequested = requestIndexing(newFile);
            SourceFile file = files.save(newFile);
            events.publishEvent(new FileReadRequested(file.getId()));
            if (indexingRequested) {
                events.publishEvent(new FileIndexRequested(file.getId()));
            }
            return file;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private SourceFile reprocess(SourceFile file) {
        if (file.getStatus() == FileStatus.PROCESSING) {
            throw new IllegalStateException("O arquivo já está sendo processado");
        }
        file.requestProcessing();
        boolean indexingRequested = requestIndexing(file);
        events.publishEvent(new FileReadRequested(file.getId()));
        if (indexingRequested) {
            events.publishEvent(new FileIndexRequested(file.getId()));
        }
        return files.save(file);
    }

    /** A new indexing request only with the Assistant feature enabled for the condominium (RF-10.3). */
    private boolean requestIndexing(SourceFile file) {
        if (!features.isEnabled(file.getCondominiumId(), FeatureService.ASSISTANT)) {
            return false;
        }
        file.requestIndexing();
        return true;
    }

    private SourceFile find(UUID condominiumId, UUID id) {
        return files.findByIdAndCondominiumId(id, condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado"));
    }

    private static String copyComputingHash(InputStream input, Path target) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var hashing = new DigestInputStream(input, digest)) {
                Files.copy(hashing, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Removes accents, slashes and odd characters from the name, so it becomes a safe file name in the folder. */
    static String safeName(String original) {
        String name = original == null || original.isBlank() ? "arquivo" : Path.of(original).getFileName().toString();
        name = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        name = name.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("-{2,}", "-");
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }
}
