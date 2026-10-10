package br.com.condominioauditoria.rag.security;

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
 * The rag's RSA key pair (ADR 0003, Sub-decision 4.1 A). The private key lives in a PKCS#8 PEM file pointed to by
 * {@code RAG_CHAVE_PRIVADA_ARQUIVO}; the public key is read from a file next to it ({@code <file>.pub}, X.509 PEM) or
 * derived from the private key, and published in {@code ListarProvedores.chave_publica_pem}.
 *
 * Without a file and with {@code RAG_GERAR_CHAVE_DEV=true} (default only in compose), generates an RSA 3072 pair and
 * writes both files with permission 600, to survive restarts. Without a file and without permission to generate, the
 * rag starts without a pair: {@code ListarProvedores} comes without a public key and {@code Perguntar} rejects with
 * FAILED_PRECONDITION.
 *
 * The private key never goes to the log; neither does the condominium's API key, opened by {@link KeyEnvelope}.
 */
@Component
public class RagKeys {

    private static final Logger log = LoggerFactory.getLogger(RagKeys.class);

    private static final int BITS = 3072;
    private static final String PRIVATE_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PRIVATE_END = "-----END PRIVATE KEY-----";
    private static final String PUBLIC_BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_END = "-----END PUBLIC KEY-----";
    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rw-------");

    private final PrivateKey privateKey;
    private final String publicPem;

    @Autowired
    RagKeys(br.com.condominioauditoria.rag.config.properties.RagProperties properties) {
        var config = properties.assistant();
        KeyPair keyPair = load(config.privateKeyFile(), config.generateDevKey());
        this.privateKey = keyPair == null ? null : keyPair.privateKey();
        this.publicPem = keyPair == null ? "" : keyPair.publicPem();
    }

    /** For the tests: pair already built, no file. */
    public RagKeys(PrivateKey privateKey, PublicKey publicKey) {
        this.privateKey = privateKey;
        this.publicPem = publicKey == null ? "" : pem(PUBLIC_BEGIN, PUBLIC_END, publicKey.getEncoded());
    }

    public boolean hasKeyPair() {
        return privateKey != null;
    }

    /** Empty when the rag has no key pair. */
    public String publicPem() {
        return publicPem;
    }

    /**
     * Opens the envelope of the condominium's API key. Called only at request time; the result is neither kept nor
     * logged.
     */
    public String openApiKey(byte[] envelope) {
        if (privateKey == null) {
            throw new NoKeyPairException();
        }
        return KeyEnvelope.open(envelope, privateKey);
    }

    /** The rag has no private key: the condominium's key cannot be decrypted. */
    public static class NoKeyPairException extends RuntimeException {
        public NoKeyPairException() {
            super("este rag está sem par de chaves (RAG_CHAVE_PRIVADA_ARQUIVO não configurado)");
        }
    }

    private record KeyPair(PrivateKey privateKey, String publicPem) {
    }

    private static KeyPair load(String configuredPath, boolean canGenerate) {
        if (configuredPath == null || configuredPath.isBlank()) {
            log.warn("RAG_CHAVE_PRIVADA_ARQUIVO não configurado: o chat do assistente fica indisponível "
                    + "(ListarProvedores sem chave pública, Perguntar recusa)");
            return null;
        }
        Path file = Path.of(configuredPath);
        Path publicFile = Path.of(configuredPath + ".pub");
        try {
            if (Files.exists(file)) {
                PrivateKey privateKey = readPrivate(Files.readString(file, StandardCharsets.UTF_8));
                String publicKey = Files.exists(publicFile)
                        ? normalizePublic(Files.readString(publicFile, StandardCharsets.UTF_8))
                        : pem(PUBLIC_BEGIN, PUBLIC_END, derivePublic(privateKey).getEncoded());
                log.info("Par de chaves do assistente carregado de {}", file);
                return new KeyPair(privateKey, publicKey);
            }
            if (!canGenerate) {
                log.warn("Arquivo de chave privada {} não existe e RAG_GERAR_CHAVE_DEV não está ligado: o chat do "
                        + "assistente fica indisponível", file);
                return null;
            }
            return generate(file, publicFile);
        } catch (IOException error) {
            throw new UncheckedIOException("Não foi possível ler o par de chaves em " + file, error);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Par de chaves inválido em " + file + ": " + error.getMessage());
        }
    }

    private static KeyPair generate(Path file, Path publicFile) throws IOException, GeneralSecurityException {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(BITS);
        var keyPair = generator.generateKeyPair();
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        writeRestricted(file, pem(PRIVATE_BEGIN, PRIVATE_END, keyPair.getPrivate().getEncoded()));
        writeRestricted(publicFile, pem(PUBLIC_BEGIN, PUBLIC_END, keyPair.getPublic().getEncoded()));
        log.warn("Par de chaves RSA {} gerado em {} (RAG_GERAR_CHAVE_DEV): só para desenvolvimento; em produção, "
                + "gere o par fora do serviço e monte como segredo", BITS, file);
        return new KeyPair(keyPair.getPrivate(), pem(PUBLIC_BEGIN, PUBLIC_END, keyPair.getPublic().getEncoded()));
    }

    private static void writeRestricted(Path file, String content) throws IOException {
        Files.writeString(file, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.setPosixFilePermissions(file, OWNER_ONLY);
        } catch (UnsupportedOperationException | IOException error) {
            log.warn("Não foi possível restringir a permissão de {}: confira manualmente", file);
        }
    }

    static PrivateKey readPrivate(String pem) throws GeneralSecurityException {
        byte[] der = Base64.getMimeDecoder().decode(withoutHeader(pem, PRIVATE_BEGIN, PRIVATE_END));
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static String normalizePublic(String pem) throws GeneralSecurityException {
        byte[] der = Base64.getMimeDecoder().decode(withoutHeader(pem, PUBLIC_BEGIN, PUBLIC_END));
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        return pem(PUBLIC_BEGIN, PUBLIC_END, publicKey.getEncoded());
    }

    /** The public key of an RSA pair comes from the private key's own material (modulus and public exponent). */
    public static PublicKey derivePublic(PrivateKey privateKey) throws GeneralSecurityException {
        if (!(privateKey instanceof RSAPrivateCrtKey crt)) {
            throw new GeneralSecurityException("a chave privada não é RSA com expoente público (PKCS#8 completo)");
        }
        return KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
    }

    private static String withoutHeader(String pem, String start, String end) throws GeneralSecurityException {
        String text = pem.strip();
        int i = text.indexOf(start);
        int f = text.indexOf(end);
        if (i < 0 || f < 0) {
            throw new GeneralSecurityException("arquivo não está no formato PEM esperado (" + start + ")");
        }
        return text.substring(i + start.length(), f);
    }

    static String pem(String start, String end, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(der);
        return start + "\n" + base64 + "\n" + end + "\n";
    }

    /** Public key as an object, for the tests and for whoever needs to encrypt (the api does it with the PEM). */
    public Optional<PublicKey> publicKey() {
        if (privateKey == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(derivePublic(privateKey));
        } catch (GeneralSecurityException error) {
            return Optional.empty();
        }
    }
}
