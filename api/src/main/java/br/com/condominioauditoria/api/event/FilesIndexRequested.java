package br.com.condominioauditoria.api.event;

import java.util.List;
import java.util.UUID;

/**
 * Batch of files with a new indexing request (enabling the Assistant feature, RF-10.4). Published in the same
 * transaction that marked the files as queued; sending happens after the commit, outside the request. If the queue goes
 * down midway, the sweep resends what is still queued.
 */
public record FilesIndexRequested(UUID condominiumId, List<UUID> fileIds) {
}
