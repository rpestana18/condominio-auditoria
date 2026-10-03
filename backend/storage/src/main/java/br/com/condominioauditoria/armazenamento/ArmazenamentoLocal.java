package br.com.condominioauditoria.armazenamento;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Arquivos numa pasta do disco. Os originais nunca são alterados nem sobrescritos. */
public class ArmazenamentoLocal implements Armazenamento {

    private final Path raiz;

    public ArmazenamentoLocal(Path raiz) throws IOException {
        this.raiz = Files.createDirectories(raiz).toRealPath();
    }

    @Override
    public void guardar(String caminhoRelativo, InputStream conteudo) throws IOException {
        Path destino = resolver(caminhoRelativo);
        if (Files.exists(destino)) {
            throw new FileAlreadyExistsException(caminhoRelativo);
        }
        Files.createDirectories(destino.getParent());
        // Grava num temporário e move: nunca fica arquivo pela metade no caminho final
        Path temporario = Files.createTempFile(destino.getParent(), ".envio-", ".tmp");
        try {
            Files.copy(conteudo, temporario, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporario, destino, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporario);
        }
        destino.toFile().setWritable(false);
    }

    @Override
    public InputStream abrir(String caminhoRelativo) throws IOException {
        return Files.newInputStream(resolver(caminhoRelativo));
    }

    @Override
    public boolean existe(String caminhoRelativo) {
        return Files.exists(resolver(caminhoRelativo));
    }

    /** Impede caminhos que escapem da pasta raiz (ex.: "../../etc/passwd"). */
    private Path resolver(String caminhoRelativo) {
        Path caminho = raiz.resolve(caminhoRelativo).normalize();
        if (!caminho.startsWith(raiz)) {
            throw new IllegalArgumentException("Caminho fora da pasta de dados: " + caminhoRelativo);
        }
        return caminho;
    }
}
