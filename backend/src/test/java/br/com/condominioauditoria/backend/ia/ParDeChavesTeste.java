package br.com.condominioauditoria.backend.ia;

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
 * Par RSA gerado no teste (como o rag gera o de desenvolvimento) e o lado do rag do envelope, escrito aqui de forma
 * independente, byte a byte como em contracts/grpc/assistente/v1 (ConfiguracaoPergunta.chave_cifrada).
 */
public final class ParDeChavesTeste {

    private static KeyPair par3072;

    private ParDeChavesTeste() {
    }

    public static synchronized KeyPair par() {
        if (par3072 == null) {
            par3072 = gerar(3072);
        }
        return par3072;
    }

    public static KeyPair gerar(int bits) {
        try {
            KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
            gerador.initialize(bits);
            return gerador.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** PEM X.509 SubjectPublicKeyInfo, como o rag manda em chave_publica_pem. */
    public static String pemPublica(KeyPair par) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(par.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
    }

    /** O que o rag faz ao receber o envelope: lê versão, N, chave AES cifrada, nonce e texto cifrado + tag. */
    public static String decifrar(byte[] envelope, PrivateKey privada) throws Exception {
        ByteBuffer b = ByteBuffer.wrap(envelope);
        byte versao = b.get();
        if (versao != 0x01) {
            throw new IllegalArgumentException("versão " + versao);
        }
        int n = Short.toUnsignedInt(b.getShort());
        byte[] chaveAesCifrada = new byte[n];
        b.get(chaveAesCifrada);
        byte[] nonce = new byte[12];
        b.get(nonce);
        byte[] cifrado = new byte[b.remaining()];
        b.get(cifrado);

        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.DECRYPT_MODE, privada, new javax.crypto.spec.OAEPParameterSpec("SHA-256", "MGF1",
                java.security.spec.MGF1ParameterSpec.SHA256, javax.crypto.spec.PSource.PSpecified.DEFAULT));
        byte[] chaveAes = rsa.doFinal(chaveAesCifrada);
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(chaveAes, "AES"), new GCMParameterSpec(128, nonce));
        byte[] aberto = aes.doFinal(cifrado);
        try {
            return new String(aberto, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(aberto, (byte) 0);
        }
    }
}
