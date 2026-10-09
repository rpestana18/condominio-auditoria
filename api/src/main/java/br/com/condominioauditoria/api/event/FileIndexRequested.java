package br.com.condominioauditoria.api.event;

import java.util.UUID;

/**
 * Published by whoever saves or reprocesses the file, after {@code SourceFile.requestIndexing()}; fires only after the
 * commit.
 */
public record FileIndexRequested(UUID fileId) {
}
