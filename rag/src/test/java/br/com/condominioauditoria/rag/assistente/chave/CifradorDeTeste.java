package br.com.condominioauditoria.rag.assistente.chave;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * O lado do backend do envelope, escrito aqui só para o teste: monta os bytes exatamente como
 * contracts/grpc/assistente/v1/assistente.proto descreve. Se o rag mudar o formato sem mudar o contrato, o teste de
 * ida e volta quebra.
 */
final class CifradorDeTeste {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private CifradorDeTeste() {
    }

    static byte[] cifrar(String chaveDeApi, PublicKey publica) throws Exception {
        KeyGenerator gerador = KeyGenerator.getInstance("AES");
        gerador.init(256);
        SecretKey aes = gerador.generateKey();

        Cipher rsa = Cipher.getInstance(EnvelopeChave.TRANSFORMACAO_RSA);
        rsa.init(Cipher.ENCRYPT_MODE, publica, EnvelopeChave.parametrosOaep());
        byte[] chaveCifrada = rsa.doFinal(aes.getEncoded());

        byte[] nonce = new byte[EnvelopeChave.TAMANHO_NONCE];
        ALEATORIO.nextBytes(nonce);
        Cipher gcm = Cipher.getInstance(EnvelopeChave.TRANSFORMACAO_AES);
        gcm.init(Cipher.ENCRYPT_MODE, aes, new GCMParameterSpec(EnvelopeChave.BITS_TAG, nonce));
        byte[] textoCifrado = gcm.doFinal(chaveDeApi.getBytes(StandardCharsets.UTF_8));

        byte[] envelope = new byte[3 + chaveCifrada.length + nonce.length + textoCifrado.length];
        envelope[0] = EnvelopeChave.VERSAO;
        envelope[1] = (byte) (chaveCifrada.length >> 8);
        envelope[2] = (byte) chaveCifrada.length;
        System.arraycopy(chaveCifrada, 0, envelope, 3, chaveCifrada.length);
        System.arraycopy(nonce, 0, envelope, 3 + chaveCifrada.length, nonce.length);
        System.arraycopy(textoCifrado, 0, envelope, 3 + chaveCifrada.length + nonce.length, textoCifrado.length);
        return envelope;
    }
}
