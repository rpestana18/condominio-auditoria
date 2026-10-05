package br.com.condominioauditoria.rag.assistente.chave;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.rag.config.PropriedadesRag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Par de chaves em arquivo: gera quando permitido, relê no reinício e não gera quando não pode. */
class ChavesRagArquivoTest {

    @Test
    void geraOParQuandoPermitidoEOReleNoReinicio(@TempDir Path pasta) throws Exception {
        Path arquivo = pasta.resolve("chaves/rag.pem");

        var primeira = new ChavesRag(propriedades(arquivo, true));

        assertThat(primeira.temPar()).isTrue();
        assertThat(arquivo).exists();
        assertThat(Files.readString(arquivo)).startsWith("-----BEGIN PRIVATE KEY-----");
        assertThat(Files.getPosixFilePermissions(arquivo)).containsExactlyInAnyOrder(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);

        // Reinício: mesma chave pública, para a chave já cadastrada no backend continuar valendo
        var segunda = new ChavesRag(propriedades(arquivo, true));
        assertThat(segunda.publicaPem()).isEqualTo(primeira.publicaPem());

        byte[] envelope = CifradorDeTeste.cifrar("sk-ant-teste", segunda.publica().orElseThrow());
        assertThat(primeira.abrirChaveDeApi(envelope)).isEqualTo("sk-ant-teste");
    }

    @Test
    void semArquivoESemPermissaoDeGerarORagSobeSemPar(@TempDir Path pasta) {
        var chaves = new ChavesRag(propriedades(pasta.resolve("nao-existe.pem"), false));

        assertThat(chaves.temPar()).isFalse();
        assertThat(chaves.publicaPem()).isEmpty();
    }

    @Test
    void semCaminhoConfiguradoORagSobeSemPar() {
        var chaves = new ChavesRag(propriedades(null, true));

        assertThat(chaves.temPar()).isFalse();
    }

    private static PropriedadesRag propriedades(Path arquivo, boolean gerar) {
        var assistente = new PropriedadesRag.Assistente(arquivo == null ? "" : arquivo.toString(), gerar,
                "https://api.anthropic.com", 90, 2, 16000, "medium", 6, 10, "backend:9090", 20,
                List.of("desvio", "fraude"));
        return new PropriedadesRag(null, null, null, null, null, assistente);
    }
}
