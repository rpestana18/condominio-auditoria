package br.com.condominioauditoria.api.listener;

import br.com.condominioauditoria.api.event.FeatureChanged;
import br.com.condominioauditoria.api.event.FilesIndexRequested;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Enabling the Assistant indexes what already exists (RF-10.4): every file of the condominium gets a new request
 * (queued, visible on the Files screen) in the same transaction as the change, and the messages go out in a batch after
 * the commit, outside the request. The rag skips what is already indexed with the same hash and model (RF-10.5, Q14).
 *
 * Disabling does nothing here: the index and the originals are kept and no WITHDRAW message is published.
 */
@Component
class ReindexOnAssistantEnabledListener {

    private final SourceFileRepository files;
    private final ApplicationEventPublisher events;

    ReindexOnAssistantEnabledListener(SourceFileRepository files, ApplicationEventPublisher events) {
        this.files = files;
        this.events = events;
    }

    @EventListener
    void onFeatureChanged(FeatureChanged change) {
        if (!change.enabled() || !FeatureService.ASSISTANT.equals(change.feature())) {
            return;
        }
        List<SourceFile> condominiumFiles = files.findByCondominiumIdOrderByUploadedAtDesc(change.condominiumId());
        if (condominiumFiles.isEmpty()) {
            return;
        }
        condominiumFiles.forEach(SourceFile::requestIndexing);
        List<UUID> ids = condominiumFiles.stream().map(SourceFile::getId).toList();
        events.publishEvent(new FilesIndexRequested(change.condominiumId(), ids));
    }
}
