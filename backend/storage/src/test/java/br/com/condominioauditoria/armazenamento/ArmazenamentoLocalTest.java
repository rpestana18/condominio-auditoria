package br.com.condominioauditoria.armazenamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArmazenamentoLocalTest {

    @TempDir
    Path pasta;

    @Test
    void guardaELeOArquivo() throws Exception {
        var armazenamento = new ArmazenamentoLocal(pasta);
        armazenamento.guardar("c1/PO/2026/po.pdf", new ByteArrayInputStream("conteudo".getBytes()));

        assertThat(armazenamento.existe("c1/PO/2026/po.pdf")).isTrue();
        try (var entrada = armazenamento.abrir("c1/PO/2026/po.pdf")) {
            assertThat(new String(entrada.readAllBytes())).isEqualTo("conteudo");
        }
    }

    @Test
    void naoSobrescreveOriginal() throws Exception {
        var armazenamento = new ArmazenamentoLocal(pasta);
        armazenamento.guardar("a.pdf", new ByteArrayInputStream(new byte[] {1}));

        assertThatThrownBy(() -> armazenamento.guardar("a.pdf", new ByteArrayInputStream(new byte[] {2})))
                .isInstanceOf(FileAlreadyExistsException.class);
    }

    @Test
    void recusaCaminhoForaDaPasta() throws Exception {
        var armazenamento = new ArmazenamentoLocal(pasta);

        assertThatThrownBy(() -> armazenamento.existe("../fora.txt")).isInstanceOf(IllegalArgumentException.class);
    }
}
