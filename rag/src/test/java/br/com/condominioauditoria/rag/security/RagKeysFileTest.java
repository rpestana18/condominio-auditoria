package br.com.condominioauditoria.rag.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.rag.config.properties.RagProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Key pair in a file: generates it when allowed, rereads it on restart and does not generate when not allowed. */
class RagKeysFileTest {

    @Test
    void generatesPairWhenAllowedAndRereadsOnRestart(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("chaves/rag.pem");

        var first = new RagKeys(properties(file, true));

        assertThat(first.hasKeyPair()).isTrue();
        assertThat(file).exists();
        assertThat(Files.readString(file)).startsWith("-----BEGIN PRIVATE KEY-----");
        assertThat(Files.getPosixFilePermissions(file)).containsExactlyInAnyOrder(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);

        // Restart: same public key, so the key already registered in the api stays valid
        var second = new RagKeys(properties(file, true));
        assertThat(second.publicPem()).isEqualTo(first.publicPem());

        byte[] envelope = TestEncryptor.encrypt("sk-ant-teste", second.publicKey().orElseThrow());
        assertThat(first.openApiKey(envelope)).isEqualTo("sk-ant-teste");
    }

    @Test
    void withoutFileAndPermissionToGenerateRagStartsWithoutPair(@TempDir Path folder) {
        var keys = new RagKeys(properties(folder.resolve("nao-existe.pem"), false));

        assertThat(keys.hasKeyPair()).isFalse();
        assertThat(keys.publicPem()).isEmpty();
    }

    @Test
    void withoutConfiguredPathRagStartsWithoutPair() {
        var keys = new RagKeys(properties(null, true));

        assertThat(keys.hasKeyPair()).isFalse();
    }

    private static RagProperties properties(Path file, boolean generate) {
        var assistant = new RagProperties.Assistant(file == null ? "" : file.toString(), generate,
                "https://api.anthropic.com", 90, 2, 16000, "medium", 6, 10, "backend:9090", 20,
                List.of("desvio", "fraude"));
        return new RagProperties(null, null, null, null, null, assistant);
    }
}
