package br.com.condominioauditoria.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Files in a folder on disk. Originals are never changed nor overwritten. */
public class LocalStorage implements Storage {

    private final Path root;

    public LocalStorage(Path root) throws IOException {
        this.root = Files.createDirectories(root).toRealPath();
    }

    @Override
    public void store(String relativePath, InputStream content) throws IOException {
        Path target = resolve(relativePath);
        if (Files.exists(target)) {
            throw new FileAlreadyExistsException(relativePath);
        }
        Files.createDirectories(target.getParent());
        // Writes to a temporary file and moves it: a half-written file never shows up at the final path
        Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
        try {
            Files.copy(content, temporary, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
        target.toFile().setWritable(false);
    }

    @Override
    public InputStream open(String relativePath) throws IOException {
        return Files.newInputStream(resolve(relativePath));
    }

    @Override
    public boolean exists(String relativePath) {
        return Files.exists(resolve(relativePath));
    }

    /** Rejects paths that escape the root folder (e.g. "../../etc/passwd"). */
    private Path resolve(String relativePath) {
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Caminho fora da pasta de dados: " + relativePath);
        }
        return path;
    }
}
