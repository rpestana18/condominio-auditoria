package br.com.condominioauditoria.backend.mensagens;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Mensagens ResultadoProcessamento v2 do golden privado (data/golden/privado, fora do git), gravadas pelo rag em
 * {@code ResultadoGoldenTest}. O backend lê como lê da fila: pelo contrato, validando o JSON Schema. Nenhuma classe do
 * rag é usada (CLAUDE.md, ADR 0002).
 */
public final class MensagensGolden {

    public static final String FLUXO_SETEMBRO = "fluxo-caixa-2026-09";
    public static final String PO_2026_2027 = "po-2026-2027";

    private MensagensGolden() {
    }

    public static Path privado() {
        return Path.of(System.getProperty("golden.dir", "../data/golden"), "privado");
    }

    /** Vazio quando o golden privado não está na máquina (o teste é pulado). */
    public static Optional<ResultadoProcessamento> ler(String nome) {
        Path arquivo = privado().resolve(nome + ".resultado-v2.json");
        if (!Files.exists(arquivo)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ContratoMensagens().lerResultado(Files.readAllBytes(arquivo)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Outro arquivo do golden privado (ex.: CSV do piloto), ou vazio. */
    public static Optional<String> texto(String arquivo) {
        Path p = privado().resolve(arquivo);
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
