package br.com.condominioauditoria.app.arquivo;

import br.com.condominioauditoria.app.processamento.FilaProcessamento.ArquivoNaFila;
import br.com.condominioauditoria.armazenamento.Armazenamento;
import br.com.condominioauditoria.dominio.Categoria;
import br.com.condominioauditoria.dominio.StatusArquivo;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Year;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Recebe arquivos: guarda o original na pasta, registra no banco e coloca na fila. */
@Service
public class ArquivoService {

    private final ArquivoRepository arquivos;
    private final Armazenamento armazenamento;
    private final ApplicationEventPublisher eventos;

    ArquivoService(ArquivoRepository arquivos, Armazenamento armazenamento, ApplicationEventPublisher eventos) {
        this.arquivos = arquivos;
        this.armazenamento = armazenamento;
        this.eventos = eventos;
    }

    @Transactional
    public Arquivo receber(UUID condominioId, Categoria categoria, MultipartFile envio, String usuario) throws IOException {
        Path temporario = Files.createTempFile("envio-", ".bin");
        try {
            String sha256;
            try (InputStream entrada = envio.getInputStream()) {
                sha256 = copiarCalculandoHash(entrada, temporario);
            }
            var existente = arquivos.findByCondominioIdAndSha256(condominioId, sha256);
            if (existente.isPresent()) {
                throw new ArquivoDuplicadoException(existente.get());
            }
            String nome = nomeSeguro(envio.getOriginalFilename());
            String caminho = "%s/%s/%d/%s-%s".formatted(condominioId, categoria, Year.now().getValue(),
                    sha256.substring(0, 12), nome);
            if (!armazenamento.existe(caminho)) {
                try (InputStream entrada = Files.newInputStream(temporario)) {
                    armazenamento.guardar(caminho, entrada);
                }
            }
            Arquivo arquivo = arquivos.save(new Arquivo(condominioId, categoria, nome, caminho, sha256,
                    Files.size(temporario), envio.getContentType(), usuario));
            eventos.publishEvent(new ArquivoNaFila(arquivo.getId()));
            return arquivo;
        } finally {
            Files.deleteIfExists(temporario);
        }
    }

    /** Reprocessar é seguro: a gravação apaga a extração anterior do arquivo antes de inserir a nova. */
    @Transactional
    public Arquivo reprocessar(Arquivo arquivo) {
        if (arquivo.getStatus() == StatusArquivo.PROCESSANDO) {
            throw new IllegalStateException("O arquivo já está sendo processado");
        }
        arquivo.voltarParaFila();
        eventos.publishEvent(new ArquivoNaFila(arquivo.getId()));
        return arquivos.save(arquivo);
    }

    private static String copiarCalculandoHash(InputStream entrada, Path destino) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var comHash = new DigestInputStream(entrada, digest)) {
                Files.copy(comHash, destino, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Remove acentos, barras e caracteres estranhos do nome, para virar um nome de arquivo seguro na pasta. */
    static String nomeSeguro(String original) {
        String nome = original == null || original.isBlank() ? "arquivo" : Path.of(original).getFileName().toString();
        nome = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        nome = nome.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("-{2,}", "-");
        return nome.length() > 120 ? nome.substring(nome.length() - 120) : nome;
    }

    public static class ArquivoDuplicadoException extends RuntimeException {
        private final Arquivo existente;

        ArquivoDuplicadoException(Arquivo existente) {
            super("Este arquivo já foi enviado em " + existente.getEnviadoEm() + " (" + existente.getNomeOriginal() + ")");
            this.existente = existente;
        }

        public Arquivo existente() {
            return existente;
        }
    }
}
