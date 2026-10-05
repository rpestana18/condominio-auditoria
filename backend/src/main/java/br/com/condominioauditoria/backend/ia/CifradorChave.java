package br.com.condominioauditoria.backend.ia;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/**
 * Cifra a chave de API do condomínio com a chave pública do rag (ADR 0003, Sub-decisão 4.1 A), no envelope de
 * contracts/grpc/assistente/v1 (ConfiguracaoPergunta.chave_cifrada), só com a biblioteca padrão do Java:
 *
 * <pre>
 * [0]               versão = 0x01
 * [1..2]            N = tamanho da chave AES cifrada (2 bytes, big endian; 384 para RSA 3072)
 * [3..3+N-1]        chave AES-256 aleatória cifrada com RSA-OAEP (SHA-256, MGF1-SHA-256, rótulo vazio)
 * [3+N..3+N+11]     nonce do GCM (12 bytes aleatórios)
 * [3+N+12..fim]     chave de API (UTF-8) cifrada com AES-256-GCM + tag de 16 bytes, sem dados adicionais
 * </pre>
 *
 * O backend só cifra: não tem a chave privada e não consegue ler a chave depois de salva. Nenhuma mensagem de erro
 * daqui leva a chave ou pedaço dela.
 */
public final class CifradorChave {

    public static final byte VERSAO = 0x01;
    public static final String RSA = "RSA/ECB/OAEPPadding";
    public static final OAEPParameterSpec OAEP = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT);
    public static final String AES = "AES/GCM/NoPadding";
    public static final int TAMANHO_NONCE = 12;
    public static final int BITS_TAG = 128;
    static final int BITS_RSA_MINIMO = 2048;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private CifradorChave() {
    }

    /** Chave pública em PEM X.509 SubjectPublicKeyInfo ("-----BEGIN PUBLIC KEY-----"), como vem de ListarProvedores. */
    public static PublicKey chavePublica(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("Chave pública do rag ausente");
        }
        String base64 = pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        RSAPublicKey chave;
        try {
            chave = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException erro) {
            throw new IllegalArgumentException("Chave pública do rag inválida (esperado PEM RSA X.509)", erro);
        }
        if (chave.getModulus().bitLength() < BITS_RSA_MINIMO) {
            throw new IllegalArgumentException("Chave pública do rag curta demais (mínimo " + BITS_RSA_MINIMO
                    + " bits)");
        }
        return chave;
    }

    /** Monta o envelope versão 1. Cada chamada usa chave AES e nonce novos. */
    public static byte[] cifrar(String chaveApi, PublicKey chavePublicaRag) {
        if (chaveApi == null || chaveApi.isEmpty()) {
            throw new IllegalArgumentException("Chave de API vazia");
        }
        byte[] aberto = chaveApi.getBytes(StandardCharsets.UTF_8);
        try {
            KeyGenerator gerador = KeyGenerator.getInstance("AES");
            gerador.init(256, ALEATORIO);
            SecretKey chaveAes = gerador.generateKey();

            Cipher rsa = Cipher.getInstance(RSA);
            rsa.init(Cipher.ENCRYPT_MODE, chavePublicaRag, OAEP, ALEATORIO);
            byte[] chaveAesCifrada = rsa.doFinal(chaveAes.getEncoded());
            if (chaveAesCifrada.length > 0xFFFF) {
                throw new IllegalArgumentException("Chave pública do rag grande demais para o envelope");
            }

            byte[] nonce = new byte[TAMANHO_NONCE];
            ALEATORIO.nextBytes(nonce);
            Cipher aes = Cipher.getInstance(AES);
            aes.init(Cipher.ENCRYPT_MODE, chaveAes, new GCMParameterSpec(BITS_TAG, nonce));
            byte[] cifrado = aes.doFinal(aberto);

            return ByteBuffer.allocate(1 + 2 + chaveAesCifrada.length + TAMANHO_NONCE + cifrado.length)
                    .put(VERSAO)
                    .putShort((short) chaveAesCifrada.length)
                    .put(chaveAesCifrada)
                    .put(nonce)
                    .put(cifrado)
                    .array();
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("Falha ao cifrar a chave de IA com a chave pública do rag", erro);
        } finally {
            Arrays.fill(aberto, (byte) 0);
        }
    }

    /** Os 4 últimos caracteres, para a tela mostrar qual chave está cadastrada. */
    public static String finalDaChave(String chaveApi) {
        return chaveApi.length() <= 4 ? chaveApi : chaveApi.substring(chaveApi.length() - 4);
    }
}
