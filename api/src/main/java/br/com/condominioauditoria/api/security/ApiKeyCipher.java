package br.com.condominioauditoria.api.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/**
 * Encrypts the condominium's API key with the rag's public key (ADR 0003, Sub-decision 4.1 A), in the envelope of
 * contracts/grpc/assistant/v2 (AskConfiguration.encrypted_key), using only the Java standard library:
 *
 * <pre>
 * [0]               version = 0x01
 * [1..2]            N = length of the encrypted AES key (2 bytes, big endian; 384 for RSA 3072)
 * [3..3+N-1]        random AES-256 key encrypted with RSA-OAEP (SHA-256, MGF1-SHA-256, empty label)
 * [3+N..3+N+11]     GCM nonce (12 random bytes)
 * [3+N+12..end]     API key (UTF-8) encrypted with AES-256-GCM + 16-byte tag, no additional data
 * </pre>
 *
 * The api only encrypts: it does not have the private key and cannot read the key once saved. No error message from
 * here carries the key or part of it.
 */
public final class ApiKeyCipher {

    public static final byte VERSION = 0x01;
    public static final String RSA = "RSA/ECB/OAEPPadding";
    public static final OAEPParameterSpec OAEP = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT);
    public static final String AES = "AES/GCM/NoPadding";
    public static final int NONCE_LENGTH = 12;
    public static final int TAG_BITS = 128;
    static final int MIN_RSA_BITS = 2048;

    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeyCipher() {
    }

    /**
     * Public key in PEM X.509 SubjectPublicKeyInfo ("-----BEGIN PUBLIC KEY-----"), as it comes from ListProviders.
     */
    public static PublicKey publicKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("Chave pública do rag ausente");
        }
        String base64 = pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        RSAPublicKey key;
        try {
            key = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException error) {
            throw new IllegalArgumentException("Chave pública do rag inválida (esperado PEM RSA X.509)", error);
        }
        if (key.getModulus().bitLength() < MIN_RSA_BITS) {
            throw new IllegalArgumentException("Chave pública do rag curta demais (mínimo " + MIN_RSA_BITS
                    + " bits)");
        }
        return key;
    }

    /** Builds the version 1 envelope. Each call uses a new AES key and nonce. */
    public static byte[] encrypt(String apiKey, PublicKey ragPublicKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("Chave de API vazia");
        }
        byte[] plain = apiKey.getBytes(StandardCharsets.UTF_8);
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256, RANDOM);
            SecretKey aesKey = generator.generateKey();

            Cipher rsa = Cipher.getInstance(RSA);
            rsa.init(Cipher.ENCRYPT_MODE, ragPublicKey, OAEP, RANDOM);
            byte[] encryptedAesKey = rsa.doFinal(aesKey.getEncoded());
            if (encryptedAesKey.length > 0xFFFF) {
                throw new IllegalArgumentException("Chave pública do rag grande demais para o envelope");
            }

            byte[] nonce = new byte[NONCE_LENGTH];
            RANDOM.nextBytes(nonce);
            Cipher aes = Cipher.getInstance(AES);
            aes.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = aes.doFinal(plain);

            return ByteBuffer.allocate(1 + 2 + encryptedAesKey.length + NONCE_LENGTH + ciphertext.length)
                    .put(VERSION)
                    .putShort((short) encryptedAesKey.length)
                    .put(encryptedAesKey)
                    .put(nonce)
                    .put(ciphertext)
                    .array();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Falha ao cifrar a chave de IA com a chave pública do rag", error);
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    /** The last 4 characters, so the screen can show which key is registered. */
    public static String keySuffix(String apiKey) {
        return apiKey.length() <= 4 ? apiKey : apiKey.substring(apiKey.length() - 4);
    }
}
