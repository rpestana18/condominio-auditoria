package br.com.condominioauditoria.rag.assistente.chave;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Par de chaves RSA do rag (ADR 0003, Sub-decisão 4.1 A). A chave privada fica em arquivo PEM PKCS#8 apontado por
 * {@code RAG_CHAVE_PRIVADA_ARQUIVO}; a pública é lida de um arquivo ao lado ({@code <arquivo>.pub}, PEM X.509) ou
 * derivada da privada e publicada em {@code ListarProvedores.chave_publica_pem}.
 *
 * Sem arquivo e com {@code RAG_GERAR_CHAVE_DEV=true} (padrão só no compose), gera um par RSA 3072 e grava os dois
 * arquivos com permissão 600, para sobreviver a reinícios. Sem arquivo e sem permissão de gerar, o rag sobe sem par:
 * {@code ListarProvedores} vem sem chave pública e {@code Perguntar} recusa com FAILED_PRECONDITION.
 *
 * A chave privada nunca vai para log; a chave de API do condomínio, aberta pelo {@link EnvelopeChave}, também não.
 */
@Component
public class ChavesRag {

    private static final Logger log = LoggerFactory.getLogger(ChavesRag.class);

    private static final int BITS = 3072;
    private static final String INICIO_PRIVADA = "-----BEGIN PRIVATE KEY-----";
    private static final String FIM_PRIVADA = "-----END PRIVATE KEY-----";
    private static final String INICIO_PUBLICA = "-----BEGIN PUBLIC KEY-----";
    private static final String FIM_PUBLICA = "-----END PUBLIC KEY-----";
    private static final Set<PosixFilePermission> SO_DONO = PosixFilePermissions.fromString("rw-------");

    private final PrivateKey privada;
    private final String publicaPem;

    @Autowired
    ChavesRag(br.com.condominioauditoria.rag.config.PropriedadesRag propriedades) {
        var config = propriedades.assistente();
        Par par = carregar(config.chavePrivadaArquivo(), config.gerarChaveDev());
        this.privada = par == null ? null : par.privada();
        this.publicaPem = par == null ? "" : par.publicaPem();
    }

    /** Para os testes: par já pronto, sem arquivo. */
    public ChavesRag(PrivateKey privada, PublicKey publica) {
        this.privada = privada;
        this.publicaPem = publica == null ? "" : pem(INICIO_PUBLICA, FIM_PUBLICA, publica.getEncoded());
    }

    public boolean temPar() {
        return privada != null;
    }

    /** Vazia quando o rag está sem par de chaves. */
    public String publicaPem() {
        return publicaPem;
    }

    /**
     * Abre o envelope da chave de API do condomínio. Só chamada na hora do pedido; o resultado não é guardado nem
     * registrado.
     */
    public String abrirChaveDeApi(byte[] envelope) {
        if (privada == null) {
            throw new SemParDeChavesException();
        }
        return EnvelopeChave.abrir(envelope, privada);
    }

    /** O rag não tem chave privada: não dá para decifrar a chave do condomínio. */
    public static class SemParDeChavesException extends RuntimeException {
        public SemParDeChavesException() {
            super("este rag está sem par de chaves (RAG_CHAVE_PRIVADA_ARQUIVO não configurado)");
        }
    }

    private record Par(PrivateKey privada, String publicaPem) {
    }

    private static Par carregar(String caminhoConfigurado, boolean podeGerar) {
        if (caminhoConfigurado == null || caminhoConfigurado.isBlank()) {
            log.warn("RAG_CHAVE_PRIVADA_ARQUIVO não configurado: o chat do assistente fica indisponível "
                    + "(ListarProvedores sem chave pública, Perguntar recusa)");
            return null;
        }
        Path arquivo = Path.of(caminhoConfigurado);
        Path arquivoPublico = Path.of(caminhoConfigurado + ".pub");
        try {
            if (Files.exists(arquivo)) {
                PrivateKey privada = lerPrivada(Files.readString(arquivo, StandardCharsets.UTF_8));
                String publica = Files.exists(arquivoPublico)
                        ? normalizarPublica(Files.readString(arquivoPublico, StandardCharsets.UTF_8))
                        : pem(INICIO_PUBLICA, FIM_PUBLICA, derivarPublica(privada).getEncoded());
                log.info("Par de chaves do assistente carregado de {}", arquivo);
                return new Par(privada, publica);
            }
            if (!podeGerar) {
                log.warn("Arquivo de chave privada {} não existe e RAG_GERAR_CHAVE_DEV não está ligado: o chat do "
                        + "assistente fica indisponível", arquivo);
                return null;
            }
            return gerar(arquivo, arquivoPublico);
        } catch (IOException erro) {
            throw new UncheckedIOException("Não foi possível ler o par de chaves em " + arquivo, erro);
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("Par de chaves inválido em " + arquivo + ": " + erro.getMessage());
        }
    }

    private static Par gerar(Path arquivo, Path arquivoPublico) throws IOException, GeneralSecurityException {
        var gerador = KeyPairGenerator.getInstance("RSA");
        gerador.initialize(BITS);
        var par = gerador.generateKeyPair();
        if (arquivo.getParent() != null) {
            Files.createDirectories(arquivo.getParent());
        }
        gravarRestrito(arquivo, pem(INICIO_PRIVADA, FIM_PRIVADA, par.getPrivate().getEncoded()));
        gravarRestrito(arquivoPublico, pem(INICIO_PUBLICA, FIM_PUBLICA, par.getPublic().getEncoded()));
        log.warn("Par de chaves RSA {} gerado em {} (RAG_GERAR_CHAVE_DEV): só para desenvolvimento; em produção, "
                + "gere o par fora do serviço e monte como segredo", BITS, arquivo);
        return new Par(par.getPrivate(), pem(INICIO_PUBLICA, FIM_PUBLICA, par.getPublic().getEncoded()));
    }

    private static void gravarRestrito(Path arquivo, String conteudo) throws IOException {
        Files.writeString(arquivo, conteudo, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.setPosixFilePermissions(arquivo, SO_DONO);
        } catch (UnsupportedOperationException | IOException erro) {
            log.warn("Não foi possível restringir a permissão de {}: confira manualmente", arquivo);
        }
    }

    static PrivateKey lerPrivada(String pem) throws GeneralSecurityException {
        byte[] der = Base64.getMimeDecoder().decode(semCabecalho(pem, INICIO_PRIVADA, FIM_PRIVADA));
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static String normalizarPublica(String pem) throws GeneralSecurityException {
        byte[] der = Base64.getMimeDecoder().decode(semCabecalho(pem, INICIO_PUBLICA, FIM_PUBLICA));
        PublicKey publica = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        return pem(INICIO_PUBLICA, FIM_PUBLICA, publica.getEncoded());
    }

    /** A pública de um par RSA sai do próprio material da privada (módulo e expoente público). */
    public static PublicKey derivarPublica(PrivateKey privada) throws GeneralSecurityException {
        if (!(privada instanceof RSAPrivateCrtKey crt)) {
            throw new GeneralSecurityException("a chave privada não é RSA com expoente público (PKCS#8 completo)");
        }
        return KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
    }

    private static String semCabecalho(String pem, String inicio, String fim) throws GeneralSecurityException {
        String texto = pem.strip();
        int i = texto.indexOf(inicio);
        int f = texto.indexOf(fim);
        if (i < 0 || f < 0) {
            throw new GeneralSecurityException("arquivo não está no formato PEM esperado (" + inicio + ")");
        }
        return texto.substring(i + inicio.length(), f);
    }

    static String pem(String inicio, String fim, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(der);
        return inicio + "\n" + base64 + "\n" + fim + "\n";
    }

    /** Chave pública em objeto, para os testes e para quem precisar cifrar (o backend faz isso com o PEM). */
    public Optional<PublicKey> publica() {
        if (privada == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(derivarPublica(privada));
        } catch (GeneralSecurityException erro) {
            return Optional.empty();
        }
    }
}
