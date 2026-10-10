package br.com.condominioauditoria.api.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * RSA pair generated in the test (as the rag generates the development one) and the rag's side of the envelope, written
 * here independently, byte by byte as in contracts/grpc/assistant/v2 (AskConfiguration.encrypted_key).
 */
public final class TestKeyPair {

    private static KeyPair pair3072;

    private TestKeyPair() {
    }

    public static synchronized KeyPair pair() {
        if (pair3072 == null) {
            pair3072 = generate(3072);
        }
        return pair3072;
    }

    public static KeyPair generate(int bits) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** PEM X.509 SubjectPublicKeyInfo, como o rag manda em chave_publica_pem. */
    public static String publicPem(KeyPair pair) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(pair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
    }

    /**
     * What the rag does when it receives the envelope: reads version, N, encrypted AES key, nonce and ciphertext + tag.
     */
    public static String decrypt(byte[] envelope, PrivateKey privateKey) throws Exception {
        ByteBuffer b = ByteBuffer.wrap(envelope);
        byte version = b.get();
        if (version != 0x01) {
            throw new IllegalArgumentException("versão " + version);
        }
        int n = Short.toUnsignedInt(b.getShort());
        byte[] encryptedAesKey = new byte[n];
        b.get(encryptedAesKey);
        byte[] nonce = new byte[12];
        b.get(nonce);
        byte[] ciphertext = new byte[b.remaining()];
        b.get(ciphertext);

        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.DECRYPT_MODE, privateKey, new javax.crypto.spec.OAEPParameterSpec("SHA-256", "MGF1",
                java.security.spec.MGF1ParameterSpec.SHA256, javax.crypto.spec.PSource.PSpecified.DEFAULT));
        byte[] aesKey = rsa.doFinal(encryptedAesKey);
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plain = aes.doFinal(ciphertext);
        try {
            return new String(plain, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }
}
