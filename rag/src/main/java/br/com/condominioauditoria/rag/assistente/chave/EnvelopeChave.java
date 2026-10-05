package br.com.condominioauditoria.rag.assistente.chave;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/**
 * Envelope da chave de API do condomínio (ADR 0003, Sub-decisão 4.1 A; formato byte a byte em
 * contracts/grpc/assistente/v1/assistente.proto). O backend cifra com a chave pública do rag e nunca consegue ler;
 * só o rag decifra, na hora da chamada ao provedor.
 *
 * <pre>
 *   [0]              versão = 0x01
 *   [1..2]           N = tamanho da chave AES cifrada (inteiro sem sinal de 2 bytes, big endian)
 *   [3..3+N-1]       chave AES-256 cifrada com RSA-OAEP (SHA-256, MGF1 com SHA-256, rótulo vazio)
 *   [3+N..3+N+11]    nonce do GCM (12 bytes)
 *   [3+N+12..fim]    texto cifrado com AES-256-GCM + tag de 16 bytes; aberto = chave de API em UTF-8
 * </pre>
 *
 * Só biblioteca padrão do Java. Nada daqui vai para log: a exceção diz o motivo do formato, nunca o conteúdo.
 */
public final class EnvelopeChave {

    public static final byte VERSAO = 0x01;
    public static final String TRANSFORMACAO_RSA = "RSA/ECB/OAEPPadding";
    public static final String TRANSFORMACAO_AES = "AES/GCM/NoPadding";
    public static final int TAMANHO_NONCE = 12;
    public static final int BITS_TAG = 128;
    /** Chave AES-256. */
    public static final int TAMANHO_CHAVE_AES = 32;

    private EnvelopeChave() {
    }

    /** Parâmetros do RSA-OAEP, iguais nos dois lados (SHA-256 no hash e no MGF1, rótulo vazio). */
    public static OAEPParameterSpec parametrosOaep() {
        return new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    }

    /**
     * Abre o envelope e devolve a chave de API. Versão diferente, tamanho incoerente ou tag inválida =
     * {@link ChaveIlegivelException}, que o gRPC traduz em FAILED_PRECONDITION.
     */
    public static String abrir(byte[] envelope, PrivateKey privada) {
        if (envelope == null || envelope.length < 3) {
            throw new ChaveIlegivelException("envelope vazio ou curto demais");
        }
        if (envelope[0] != VERSAO) {
            throw new ChaveIlegivelException("versão de envelope não suportada: " + (envelope[0] & 0xFF));
        }
        int tamanhoChave = ((envelope[1] & 0xFF) << 8) | (envelope[2] & 0xFF);
        int inicioNonce = 3 + tamanhoChave;
        int inicioTexto = inicioNonce + TAMANHO_NONCE;
        // Precisa sobrar ao menos a tag de 16 bytes depois do nonce
        if (tamanhoChave == 0 || envelope.length < inicioTexto + BITS_TAG / 8) {
            throw new ChaveIlegivelException("tamanhos incoerentes no envelope");
        }
        byte[] aesAberta = null;
        try {
            byte[] chaveCifrada = Arrays.copyOfRange(envelope, 3, inicioNonce);
            Cipher rsa = Cipher.getInstance(TRANSFORMACAO_RSA);
            rsa.init(Cipher.DECRYPT_MODE, privada, parametrosOaep());
            aesAberta = rsa.doFinal(chaveCifrada);
            if (aesAberta.length != TAMANHO_CHAVE_AES) {
                throw new ChaveIlegivelException("a chave simétrica do envelope não tem 32 bytes");
            }
            Cipher aes = Cipher.getInstance(TRANSFORMACAO_AES);
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesAberta, "AES"),
                    new GCMParameterSpec(BITS_TAG, envelope, inicioNonce, TAMANHO_NONCE));
            byte[] aberto = aes.doFinal(envelope, inicioTexto, envelope.length - inicioTexto);
            String chave = new String(aberto, StandardCharsets.UTF_8);
            Arrays.fill(aberto, (byte) 0);
            if (chave.isBlank()) {
                throw new ChaveIlegivelException("a chave de API aberta está vazia");
            }
            return chave;
        } catch (ChaveIlegivelException erro) {
            throw erro;
        } catch (java.security.GeneralSecurityException erro) {
            // Tag inválida, chave trocada, padding errado: tudo vira o mesmo motivo, sem detalhe técnico do conteúdo
            throw new ChaveIlegivelException("o envelope não pôde ser decifrado com a chave privada deste rag");
        }
    }

    /** Envelope fora do formato, ou cifrado com outra chave pública. Nunca carrega pedaço da chave. */
    public static class ChaveIlegivelException extends RuntimeException {
        public ChaveIlegivelException(String mensagem) {
            super(mensagem);
        }
    }
}
