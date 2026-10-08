package br.com.condominioauditoria.api.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import javax.crypto.AEADBadTagException;
import org.junit.jupiter.api.Test;

/** Envelope da chave de API (ADR 0003, Sub-decisão 4.1 A): ida e volta com um par gerado aqui, no formato do proto. */
class CifradorChaveTest {

    private static final String CHAVE = "sk-ant-api03-ChaveDeTesteNaoReal-x9Qa";

    @Test
    void idaEVoltaNoFormatoDoProto() throws Exception {
        KeyPair par = ParDeChavesTeste.par();

        byte[] envelope = CifradorChave.cifrar(CHAVE, CifradorChave.chavePublica(ParDeChavesTeste.pemPublica(par)));

        ByteBuffer b = ByteBuffer.wrap(envelope);
        assertThat(b.get()).isEqualTo((byte) 0x01);
        int n = Short.toUnsignedInt(b.getShort());
        assertThat(n).isEqualTo(384); // RSA 3072
        int tamanhoTexto = CHAVE.getBytes(StandardCharsets.UTF_8).length;
        assertThat(envelope).hasSize(1 + 2 + n + 12 + tamanhoTexto + 16);
        assertThat(ParDeChavesTeste.decifrar(envelope, par.getPrivate())).isEqualTo(CHAVE);
    }

    @Test
    void chaveNuncaApareceAbertaNoEnvelopeECadaCifragemEhDiferente() throws Exception {
        var publica = CifradorChave.chavePublica(ParDeChavesTeste.pemPublica(ParDeChavesTeste.par()));

        byte[] a = CifradorChave.cifrar(CHAVE, publica);
        byte[] b = CifradorChave.cifrar(CHAVE, publica);

        assertThat(new String(a, StandardCharsets.ISO_8859_1)).doesNotContain(CHAVE).doesNotContain("x9Qa");
        assertThat(a).isNotEqualTo(b); // chave AES e nonce novos a cada vez
        assertThat(ParDeChavesTeste.decifrar(b, ParDeChavesTeste.par().getPrivate())).isEqualTo(CHAVE);
    }

    @Test
    void caracteresForaDoAsciiVoltamIguais() throws Exception {
        String chave = "chave-çãé-ü-12345";
        byte[] envelope = CifradorChave.cifrar(chave,
                CifradorChave.chavePublica(ParDeChavesTeste.pemPublica(ParDeChavesTeste.par())));
        assertThat(ParDeChavesTeste.decifrar(envelope, ParDeChavesTeste.par().getPrivate())).isEqualTo(chave);
    }

    @Test
    void envelopeAlteradoNaoDecifra() throws Exception {
        byte[] envelope = CifradorChave.cifrar(CHAVE,
                CifradorChave.chavePublica(ParDeChavesTeste.pemPublica(ParDeChavesTeste.par())));
        envelope[envelope.length - 1] ^= 0x01; // mexe na tag

        assertThatThrownBy(() -> ParDeChavesTeste.decifrar(envelope, ParDeChavesTeste.par().getPrivate()))
                .isInstanceOf(AEADBadTagException.class);
    }

    @Test
    void outroParNaoDecifra() {
        byte[] envelope = CifradorChave.cifrar(CHAVE,
                CifradorChave.chavePublica(ParDeChavesTeste.pemPublica(ParDeChavesTeste.par())));
        KeyPair outro = ParDeChavesTeste.gerar(3072);

        assertThatThrownBy(() -> ParDeChavesTeste.decifrar(envelope, outro.getPrivate())).isInstanceOf(Exception.class);
    }

    @Test
    void pemInvalidoOuCurtoEhRecusadoSemCifrar() {
        assertThatThrownBy(() -> CifradorChave.chavePublica("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CifradorChave.chavePublica("-----BEGIN PUBLIC KEY-----\nAAAA\n-----END PUBLIC KEY-----"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inválida");
        assertThatThrownBy(() -> CifradorChave.chavePublica(ParDeChavesTeste.pemPublica(ParDeChavesTeste.gerar(1024))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("curta");
    }

    @Test
    void finalDaChaveSaoOsQuatroUltimosCaracteres() {
        assertThat(CifradorChave.finalDaChave(CHAVE)).isEqualTo("x9Qa");
    }
}
