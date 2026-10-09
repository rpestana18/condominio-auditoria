package br.com.condominioauditoria.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import javax.crypto.AEADBadTagException;
import org.junit.jupiter.api.Test;

/** API key envelope (ADR 0003, Sub-decision 4.1 A): round trip with a pair generated here, in the proto's format. */
class ApiKeyCipherTest {

    private static final String KEY = "sk-ant-api03-ChaveDeTesteNaoReal-x9Qa";

    @Test
    void roundTripInProtoFormat() throws Exception {
        KeyPair pair = TestKeyPair.pair();

        byte[] envelope = ApiKeyCipher.encrypt(KEY, ApiKeyCipher.publicKey(TestKeyPair.publicPem(pair)));

        ByteBuffer b = ByteBuffer.wrap(envelope);
        assertThat(b.get()).isEqualTo((byte) 0x01);
        int n = Short.toUnsignedInt(b.getShort());
        assertThat(n).isEqualTo(384); // RSA 3072
        int textLength = KEY.getBytes(StandardCharsets.UTF_8).length;
        assertThat(envelope).hasSize(1 + 2 + n + 12 + textLength + 16);
        assertThat(TestKeyPair.decrypt(envelope, pair.getPrivate())).isEqualTo(KEY);
    }

    @Test
    void keyNeverAppearsInClearAndEachEncryptionDiffers() throws Exception {
        var publicKey = ApiKeyCipher.publicKey(TestKeyPair.publicPem(TestKeyPair.pair()));

        byte[] a = ApiKeyCipher.encrypt(KEY, publicKey);
        byte[] b = ApiKeyCipher.encrypt(KEY, publicKey);

        assertThat(new String(a, StandardCharsets.ISO_8859_1)).doesNotContain(KEY).doesNotContain("x9Qa");
        assertThat(a).isNotEqualTo(b); // chave AES e nonce novos a cada vez
        assertThat(TestKeyPair.decrypt(b, TestKeyPair.pair().getPrivate())).isEqualTo(KEY);
    }

    @Test
    void nonAsciiCharactersRoundTrip() throws Exception {
        String key = "chave-çãé-ü-12345";
        byte[] envelope = ApiKeyCipher.encrypt(key,
                ApiKeyCipher.publicKey(TestKeyPair.publicPem(TestKeyPair.pair())));
        assertThat(TestKeyPair.decrypt(envelope, TestKeyPair.pair().getPrivate())).isEqualTo(key);
    }

    @Test
    void tamperedEnvelopeDoesNotDecrypt() throws Exception {
        byte[] envelope = ApiKeyCipher.encrypt(KEY,
                ApiKeyCipher.publicKey(TestKeyPair.publicPem(TestKeyPair.pair())));
        envelope[envelope.length - 1] ^= 0x01; // tamper with the tag

        assertThatThrownBy(() -> TestKeyPair.decrypt(envelope, TestKeyPair.pair().getPrivate()))
                .isInstanceOf(AEADBadTagException.class);
    }

    @Test
    void otherKeyPairDoesNotDecrypt() {
        byte[] envelope = ApiKeyCipher.encrypt(KEY,
                ApiKeyCipher.publicKey(TestKeyPair.publicPem(TestKeyPair.pair())));
        KeyPair other = TestKeyPair.generate(3072);

        assertThatThrownBy(() -> TestKeyPair.decrypt(envelope, other.getPrivate())).isInstanceOf(Exception.class);
    }

    @Test
    void invalidOrShortPemIsRejectedWithoutEncrypting() {
        assertThatThrownBy(() -> ApiKeyCipher.publicKey("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApiKeyCipher.publicKey("-----BEGIN PUBLIC KEY-----\nAAAA\n-----END PUBLIC KEY-----"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inválida");
        assertThatThrownBy(() -> ApiKeyCipher.publicKey(TestKeyPair.publicPem(TestKeyPair.generate(1024))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("curta");
    }

    @Test
    void keySuffixIsTheLastFourCharacters() {
        assertThat(ApiKeyCipher.keySuffix(KEY)).isEqualTo("x9Qa");
    }
}
