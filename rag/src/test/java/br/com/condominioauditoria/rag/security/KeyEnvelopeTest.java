package br.com.condominioauditoria.rag.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Envelope of the condominium's AI key (ADR 0003, Sub-decision 4.1 A): round trip with the encryptor of the api side,
 * and rejection of a tampered envelope, a wrong version and a swapped key pair.
 */
class KeyEnvelopeTest {

    private static final String API_KEY = "sk-ant-api03-exemplo-de-chave-do-condominio-0123456789";

    private static KeyPair keyPair;
    private static KeyPair otherKeyPair;

    @BeforeAll
    static void generate() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        keyPair = generator.generateKeyPair();
        otherKeyPair = generator.generateKeyPair();
    }

    @Test
    void roundTripReturnsApiKey() throws Exception {
        byte[] envelope = TestEncryptor.encrypt(API_KEY, keyPair.getPublic());

        assertThat(KeyEnvelope.open(envelope, keyPair.getPrivate())).isEqualTo(API_KEY);
        // 1 (version) + 2 (size) + 384 (RSA 3072) + 12 (nonce) + text + 16 (tag)
        assertThat(envelope[0]).isEqualTo(KeyEnvelope.VERSION);
        assertThat(((envelope[1] & 0xFF) << 8) | (envelope[2] & 0xFF)).isEqualTo(384);
        assertThat(envelope).hasSize(3 + 384 + 12 + API_KEY.length() + 16);
    }

    @Test
    void envelopeTamperedInCiphertextFails() throws Exception {
        byte[] envelope = TestEncryptor.encrypt(API_KEY, keyPair.getPublic());
        envelope[envelope.length - 20] ^= 0x01;

        assertThatThrownBy(() -> KeyEnvelope.open(envelope, keyPair.getPrivate()))
                .isInstanceOf(KeyEnvelope.UnreadableKeyException.class)
                .hasMessageContaining("não pôde ser decifrado");
    }

    @Test
    void envelopeTamperedInSymmetricKeyFails() throws Exception {
        byte[] envelope = TestEncryptor.encrypt(API_KEY, keyPair.getPublic());
        envelope[10] ^= 0x7F;

        assertThatThrownBy(() -> KeyEnvelope.open(envelope, keyPair.getPrivate()))
                .isInstanceOf(KeyEnvelope.UnreadableKeyException.class);
    }

    @Test
    void swappedKeyPairFails() throws Exception {
        byte[] envelope = TestEncryptor.encrypt(API_KEY, keyPair.getPublic());

        assertThatThrownBy(() -> KeyEnvelope.open(envelope, otherKeyPair.getPrivate()))
                .isInstanceOf(KeyEnvelope.UnreadableKeyException.class);
    }

    @Test
    void unknownVersionFails() throws Exception {
        byte[] envelope = TestEncryptor.encrypt(API_KEY, keyPair.getPublic());
        envelope[0] = 0x02;

        assertThatThrownBy(() -> KeyEnvelope.open(envelope, keyPair.getPrivate()))
                .isInstanceOf(KeyEnvelope.UnreadableKeyException.class)
                .hasMessageContaining("versão de envelope");
    }

    @Test
    void inconsistentSizeFails() throws Exception {
        byte[] envelope = TestEncryptor.encrypt(API_KEY, keyPair.getPublic());
        envelope[1] = (byte) 0xFF;

        assertThatThrownBy(() -> KeyEnvelope.open(envelope, keyPair.getPrivate()))
                .isInstanceOf(KeyEnvelope.UnreadableKeyException.class)
                .hasMessageContaining("incoerentes");
    }

    @Test
    void withoutKeyPairRagRejects() {
        var keys = new RagKeys(null, null);

        assertThat(keys.hasKeyPair()).isFalse();
        assertThat(keys.publicPem()).isEmpty();
        assertThatThrownBy(() -> keys.openApiKey(new byte[] { 1 }))
                .isInstanceOf(RagKeys.NoKeyPairException.class);
    }

    @Test
    void publicKeyComesAsPemX509AndDerivedMatchesGenerated(@TempDir Path folder) throws Exception {
        var keys = new RagKeys(keyPair.getPrivate(), keyPair.getPublic());

        assertThat(keys.hasKeyPair()).isTrue();
        assertThat(keys.publicPem()).startsWith("-----BEGIN PUBLIC KEY-----")
                .endsWith("-----END PUBLIC KEY-----\n");
        assertThat(RagKeys.derivePublic(keyPair.getPrivate())).isEqualTo(keyPair.getPublic());
        // With the public key from the PEM, the api encrypts and the rag opens
        byte[] envelope = TestEncryptor.encrypt(API_KEY, RagKeys.derivePublic(keyPair.getPrivate()));
        assertThat(keys.openApiKey(envelope)).isEqualTo(API_KEY);
        // Just to make sure the temporary folder is not used by mistake
        assertThat(Files.list(folder)).isEmpty();
    }
}
