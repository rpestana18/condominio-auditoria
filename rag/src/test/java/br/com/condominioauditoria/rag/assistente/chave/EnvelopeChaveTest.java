package br.com.condominioauditoria.rag.assistente.chave;

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
 * Envelope da chave de IA do condomínio (ADR 0003, Sub-decisão 4.1 A): ida e volta com o cifrador do lado do
 * backend, e recusa de envelope adulterado, de versão errada e de par de chaves trocado.
 */
class EnvelopeChaveTest {

    private static final String CHAVE_DE_API = "sk-ant-api03-exemplo-de-chave-do-condominio-0123456789";

    private static KeyPair par;
    private static KeyPair outroPar;

    @BeforeAll
    static void gerar() throws Exception {
        var gerador = KeyPairGenerator.getInstance("RSA");
        gerador.initialize(3072);
        par = gerador.generateKeyPair();
        outroPar = gerador.generateKeyPair();
    }

    @Test
    void idaEVoltaDevolveAChaveDeApi() throws Exception {
        byte[] envelope = CifradorDeTeste.cifrar(CHAVE_DE_API, par.getPublic());

        assertThat(EnvelopeChave.abrir(envelope, par.getPrivate())).isEqualTo(CHAVE_DE_API);
        // 1 (versão) + 2 (tamanho) + 384 (RSA 3072) + 12 (nonce) + texto + 16 (tag)
        assertThat(envelope[0]).isEqualTo(EnvelopeChave.VERSAO);
        assertThat(((envelope[1] & 0xFF) << 8) | (envelope[2] & 0xFF)).isEqualTo(384);
        assertThat(envelope).hasSize(3 + 384 + 12 + CHAVE_DE_API.length() + 16);
    }

    @Test
    void envelopeAdulteradoNoTextoCifradoFalha() throws Exception {
        byte[] envelope = CifradorDeTeste.cifrar(CHAVE_DE_API, par.getPublic());
        envelope[envelope.length - 20] ^= 0x01;

        assertThatThrownBy(() -> EnvelopeChave.abrir(envelope, par.getPrivate()))
                .isInstanceOf(EnvelopeChave.ChaveIlegivelException.class)
                .hasMessageContaining("não pôde ser decifrado");
    }

    @Test
    void envelopeAdulteradoNaChaveSimetricaFalha() throws Exception {
        byte[] envelope = CifradorDeTeste.cifrar(CHAVE_DE_API, par.getPublic());
        envelope[10] ^= 0x7F;

        assertThatThrownBy(() -> EnvelopeChave.abrir(envelope, par.getPrivate()))
                .isInstanceOf(EnvelopeChave.ChaveIlegivelException.class);
    }

    @Test
    void parDeChavesTrocadoFalha() throws Exception {
        byte[] envelope = CifradorDeTeste.cifrar(CHAVE_DE_API, par.getPublic());

        assertThatThrownBy(() -> EnvelopeChave.abrir(envelope, outroPar.getPrivate()))
                .isInstanceOf(EnvelopeChave.ChaveIlegivelException.class);
    }

    @Test
    void versaoDesconhecidaFalha() throws Exception {
        byte[] envelope = CifradorDeTeste.cifrar(CHAVE_DE_API, par.getPublic());
        envelope[0] = 0x02;

        assertThatThrownBy(() -> EnvelopeChave.abrir(envelope, par.getPrivate()))
                .isInstanceOf(EnvelopeChave.ChaveIlegivelException.class)
                .hasMessageContaining("versão de envelope");
    }

    @Test
    void tamanhoIncoerenteFalha() throws Exception {
        byte[] envelope = CifradorDeTeste.cifrar(CHAVE_DE_API, par.getPublic());
        envelope[1] = (byte) 0xFF;

        assertThatThrownBy(() -> EnvelopeChave.abrir(envelope, par.getPrivate()))
                .isInstanceOf(EnvelopeChave.ChaveIlegivelException.class)
                .hasMessageContaining("incoerentes");
    }

    @Test
    void semParDeChavesORagRecusa() {
        var chaves = new ChavesRag(null, null);

        assertThat(chaves.temPar()).isFalse();
        assertThat(chaves.publicaPem()).isEmpty();
        assertThatThrownBy(() -> chaves.abrirChaveDeApi(new byte[] { 1 }))
                .isInstanceOf(ChavesRag.SemParDeChavesException.class);
    }

    @Test
    void chavePublicaSaiEmPemX509EADerivadaBateComAGerada(@TempDir Path pasta) throws Exception {
        var chaves = new ChavesRag(par.getPrivate(), par.getPublic());

        assertThat(chaves.temPar()).isTrue();
        assertThat(chaves.publicaPem()).startsWith("-----BEGIN PUBLIC KEY-----")
                .endsWith("-----END PUBLIC KEY-----\n");
        assertThat(ChavesRag.derivarPublica(par.getPrivate())).isEqualTo(par.getPublic());
        // Com a pública que sai do PEM, o backend cifra e o rag abre
        byte[] envelope = CifradorDeTeste.cifrar(CHAVE_DE_API, ChavesRag.derivarPublica(par.getPrivate()));
        assertThat(chaves.abrirChaveDeApi(envelope)).isEqualTo(CHAVE_DE_API);
        // Só para garantir que a pasta temporária não é usada por engano
        assertThat(Files.list(pasta)).isEmpty();
    }
}
