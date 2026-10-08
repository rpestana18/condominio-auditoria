package br.com.condominioauditoria.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalStorageTest {

    @TempDir
    Path folder;

    @Test
    void storesAndReadsTheFile() throws Exception {
        var storage = new LocalStorage(folder);
        storage.store("c1/PO/2026/po.pdf", new ByteArrayInputStream("content".getBytes()));

        assertThat(storage.exists("c1/PO/2026/po.pdf")).isTrue();
        try (var input = storage.open("c1/PO/2026/po.pdf")) {
            assertThat(new String(input.readAllBytes())).isEqualTo("content");
        }
    }

    @Test
    void neverOverwritesAnOriginal() throws Exception {
        var storage = new LocalStorage(folder);
        storage.store("a.pdf", new ByteArrayInputStream(new byte[] {1}));

        assertThatThrownBy(() -> storage.store("a.pdf", new ByteArrayInputStream(new byte[] {2})))
                .isInstanceOf(FileAlreadyExistsException.class);
    }

    @Test
    void rejectsPathOutsideTheFolder() throws Exception {
        var storage = new LocalStorage(folder);

        assertThatThrownBy(() -> storage.exists("../fora.txt")).isInstanceOf(IllegalArgumentException.class);
    }
}
