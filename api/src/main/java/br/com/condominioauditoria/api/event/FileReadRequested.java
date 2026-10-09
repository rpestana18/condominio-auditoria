package br.com.condominioauditoria.api.event;

import java.util.UUID;

/** Published by whoever saves or reprocesses the file; fires only after the commit. */
public record FileReadRequested(UUID fileId) {
}
