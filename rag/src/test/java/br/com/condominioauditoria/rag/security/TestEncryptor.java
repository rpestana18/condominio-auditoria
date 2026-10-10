package br.com.condominioauditoria.rag.security;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * The api side of the envelope, written here only for the test: builds the bytes exactly as
 * contracts/grpc/assistant/v2/assistant.proto describes. If the rag changes the format without changing the
 * contract, the round-trip test breaks.
 */
final class TestEncryptor {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TestEncryptor() {
    }

    static byte[] encrypt(String plainApiKey, PublicKey publicKey) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey aes = generator.generateKey();

        Cipher rsa = Cipher.getInstance(KeyEnvelope.RSA_TRANSFORMATION);
        rsa.init(Cipher.ENCRYPT_MODE, publicKey, KeyEnvelope.oaepParameters());
        byte[] encryptedKey = rsa.doFinal(aes.getEncoded());

        byte[] nonce = new byte[KeyEnvelope.NONCE_SIZE];
        RANDOM.nextBytes(nonce);
        Cipher gcm = Cipher.getInstance(KeyEnvelope.AES_TRANSFORMATION);
        gcm.init(Cipher.ENCRYPT_MODE, aes, new GCMParameterSpec(KeyEnvelope.TAG_BITS, nonce));
        byte[] ciphertext = gcm.doFinal(plainApiKey.getBytes(StandardCharsets.UTF_8));

        byte[] envelope = new byte[3 + encryptedKey.length + nonce.length + ciphertext.length];
        envelope[0] = KeyEnvelope.VERSION;
        envelope[1] = (byte) (encryptedKey.length >> 8);
        envelope[2] = (byte) encryptedKey.length;
        System.arraycopy(encryptedKey, 0, envelope, 3, encryptedKey.length);
        System.arraycopy(nonce, 0, envelope, 3 + encryptedKey.length, nonce.length);
        System.arraycopy(ciphertext, 0, envelope, 3 + encryptedKey.length + nonce.length, ciphertext.length);
        return envelope;
    }
}
