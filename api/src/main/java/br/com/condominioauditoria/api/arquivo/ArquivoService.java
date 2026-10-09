package br.com.condominioauditoria.api.arquivo;

import br.com.condominioauditoria.api.mensagens.PublicadorArquivos.ArquivoParaLer;
import br.com.condominioauditoria.api.mensagens.PublicadorIndexacao.ArquivoParaIndexar;
import br.com.condominioauditoria.api.modulo.Modulos;
import br.com.condominioauditoria.storage.Storage;
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

/**
 * Recebe arquivos: guarda o original na pasta, registra no banco e, depois do commit, pede ao rag pela fila a leitura
 * e a indexação para a busca nos documentos (filas separadas, ADR 0003, Decisão 5.1). A indexação pertence ao
 * módulo Assistente: com ele desligado no condomínio, o arquivo passa só pelo núcleo e o estado de indexação fica
 * como está (nulo no arquivo novo), sem nenhuma mensagem (RF-10.3).
 */
@Service
public class ArquivoService {

    private final ArquivoRepository arquivos;
    private final Storage armazenamento;
    private final ApplicationEventPublisher eventos;
    private final HistoricoCategoriaRepository historico;
    private final Modulos modulos;

    ArquivoService(ArquivoRepository arquivos, Storage armazenamento, ApplicationEventPublisher eventos,
            HistoricoCategoriaRepository historico, Modulos modulos) {
        this.arquivos = arquivos;
        this.armazenamento = armazenamento;
        this.eventos = eventos;
        this.historico = historico;
        this.modulos = modulos;
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
            if (!armazenamento.exists(caminho)) {
                try (InputStream entrada = Files.newInputStream(temporario)) {
                    armazenamento.store(caminho, entrada);
                }
            }
            var novo = new Arquivo(condominioId, categoria, nome, caminho, sha256, Files.size(temporario),
                    envio.getContentType(), usuario);
            boolean indexar = pedirIndexacao(novo);
            Arquivo arquivo = arquivos.save(novo);
            eventos.publishEvent(new ArquivoParaLer(arquivo.getId()));
            if (indexar) {
                eventos.publishEvent(new ArquivoParaIndexar(arquivo.getId()));
            }
            return arquivo;
        } finally {
            Files.deleteIfExists(temporario);
        }
    }

    /**
     * Reprocessar é seguro: a gravação apaga a extração anterior do arquivo antes de inserir a nova. Com o módulo
     * Assistente ligado, também reindexa (RF-04.6); o rag não refaz o que já está igual.
     */
    @Transactional
    public Arquivo reprocessar(Arquivo arquivo) {
        if (arquivo.getStatus() == StatusArquivo.PROCESSANDO) {
            throw new IllegalStateException("O arquivo já está sendo processado");
        }
        arquivo.novoProcessamento();
        boolean indexar = pedirIndexacao(arquivo);
        eventos.publishEvent(new ArquivoParaLer(arquivo.getId()));
        if (indexar) {
            eventos.publishEvent(new ArquivoParaIndexar(arquivo.getId()));
        }
        return arquivos.save(arquivo);
    }

    /** Novo pedido de indexação só com o módulo Assistente ligado no condomínio (RF-10.3). */
    private boolean pedirIndexacao(Arquivo arquivo) {
        if (!modulos.ligado(arquivo.getCondominioId(), Modulos.ASSISTENTE)) {
            return false;
        }
        arquivo.novaIndexacao();
        return true;
    }

    /**
     * Troca a categoria e reprocessa (RF-01.7): a gravação apaga a extração feita sob a categoria antiga.
     * Mesma categoria não faz nada. A troca fica registrada com quem fez, quando, a anterior e a nova.
     */
    @Transactional
    public Arquivo alterarCategoria(Arquivo arquivo, Categoria nova, String usuario) {
        if (arquivo.getCategoria() == nova) {
            return arquivo;
        }
        if (arquivo.getStatus() == StatusArquivo.PROCESSANDO) {
            throw new IllegalStateException("O arquivo já está sendo processado");
        }
        historico.save(new HistoricoCategoria(arquivo.getId(), arquivo.getCategoria(), nova, usuario));
        arquivo.trocarCategoria(nova);
        return reprocessar(arquivo);
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
