package br.com.condominioauditoria.rag.security;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/**
 * Envelope of the condominium's API key (ADR 0003, Sub-decision 4.1 A; byte-by-byte format in
 * contracts/grpc/assistant/v2/assistant.proto). The api encrypts it with the rag's public key and can never read
 * it; only the rag decrypts it, at the time of the call to the provider.
 *
 * <pre>
 *   [0]              version = 0x01
 *   [1..2]           N = size of the encrypted AES key (2-byte unsigned integer, big endian)
 *   [3..3+N-1]       AES-256 key encrypted with RSA-OAEP (SHA-256, MGF1 with SHA-256, empty label)
 *   [3+N..3+N+11]    GCM nonce (12 bytes)
 *   [3+N+12..end]    ciphertext with AES-256-GCM + 16-byte tag; plaintext = API key in UTF-8
 * </pre>
 *
 * Standard Java library only. Nothing from here goes to the log: the exception states the format reason, never the
 * content.
 */
public final class KeyEnvelope {

    public static final byte VERSION = 0x01;
    public static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPPadding";
    public static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    public static final int NONCE_SIZE = 12;
    public static final int TAG_BITS = 128;
    /** AES-256 key. */
    public static final int AES_KEY_SIZE = 32;

    private KeyEnvelope() {
    }

    /** RSA-OAEP parameters, the same on both sides (SHA-256 in the hash and in MGF1, empty label). */
    public static OAEPParameterSpec oaepParameters() {
        return new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    }

    /**
     * Opens the envelope and returns the API key. Different version, inconsistent size or invalid tag =
     * {@link UnreadableKeyException}, which the gRPC layer translates into FAILED_PRECONDITION.
     */
    public static String open(byte[] envelope, PrivateKey privateKey) {
        if (envelope == null || envelope.length < 3) {
            throw new UnreadableKeyException("envelope vazio ou curto demais");
        }
        if (envelope[0] != VERSION) {
            throw new UnreadableKeyException("versão de envelope não suportada: " + (envelope[0] & 0xFF));
        }
        int keySize = ((envelope[1] & 0xFF) << 8) | (envelope[2] & 0xFF);
        int nonceStart = 3 + keySize;
        int textStart = nonceStart + NONCE_SIZE;
        // At least the 16-byte tag must remain after the nonce
        if (keySize == 0 || envelope.length < textStart + TAG_BITS / 8) {
            throw new UnreadableKeyException("tamanhos incoerentes no envelope");
        }
        byte[] openAes = null;
        try {
            byte[] encryptedKey = Arrays.copyOfRange(envelope, 3, nonceStart);
            Cipher rsa = Cipher.getInstance(RSA_TRANSFORMATION);
            rsa.init(Cipher.DECRYPT_MODE, privateKey, oaepParameters());
            openAes = rsa.doFinal(encryptedKey);
            if (openAes.length != AES_KEY_SIZE) {
                throw new UnreadableKeyException("a chave simétrica do envelope não tem 32 bytes");
            }
            Cipher aes = Cipher.getInstance(AES_TRANSFORMATION);
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(openAes, "AES"),
                    new GCMParameterSpec(TAG_BITS, envelope, nonceStart, NONCE_SIZE));
            byte[] opened = aes.doFinal(envelope, textStart, envelope.length - textStart);
            String key = new String(opened, StandardCharsets.UTF_8);
            Arrays.fill(opened, (byte) 0);
            if (key.isBlank()) {
                throw new UnreadableKeyException("a chave de API aberta está vazia");
            }
            return key;
        } catch (UnreadableKeyException error) {
            throw error;
        } catch (java.security.GeneralSecurityException error) {
            // Invalid tag, wrong key, bad padding: all become the same reason, with no technical detail of the content
            throw new UnreadableKeyException("o envelope não pôde ser decifrado com a chave privada deste rag");
        }
    }

    /** Envelope out of format, or encrypted with another public key. Never carries any part of the key. */
    public static class UnreadableKeyException extends RuntimeException {
        public UnreadableKeyException(String message) {
            super(message);
        }
    }
}
