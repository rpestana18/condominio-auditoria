package br.com.condominioauditoria.api.messaging;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * ResultadoProcessamento v2 messages from the private golden set (data/golden/privado, outside git), written by the rag
 * in {@code ResultadoGoldenTest}. The api reads them as it reads from the queue: through the contract, validating the
 * JSON Schema. No rag class is used (CLAUDE.md, ADR 0002).
 */
public final class GoldenMessages {

    public static final String SEPTEMBER_CASH_FLOW = "fluxo-caixa-2026-09";
    public static final String BUDGET_2026_2027 = "po-2026-2027";

    private GoldenMessages() {
    }

    public static Path privateFolder() {
        return Path.of(System.getProperty("golden.dir", "../data/golden"), "privado");
    }

    /** Empty when the private golden set is not on the machine (the test is skipped). */
    public static Optional<ProcessingResultMessage> read(String name) {
        Path file = privateFolder().resolve(name + ".resultado-v2.json");
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new MessageContract().readProcessingResult(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Another file of the private golden set (e.g. the pilot's CSV), or empty. */
    public static Optional<String> text(String file) {
        Path p = privateFolder().resolve(file);
        if (!Files.exists(p)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(p));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
