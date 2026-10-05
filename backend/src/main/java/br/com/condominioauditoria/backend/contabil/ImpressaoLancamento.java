package br.com.condominioauditoria.backend.contabil;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Impressão do lançamento: o que identifica um lançamento no fluxo independentemente do {@code id}, que muda a cada
 * reprocesso (a gravação apaga e recria). Arquivo, página, ordem, data, conta do fluxo, documento e valor (ADR 0004,
 * Decisão 3). Mesma leitura do mesmo arquivo = mesma chave; se o fluxo mudou, a chave não casa e quem a usa avisa.
 * Função pura.
 */
public record ImpressaoLancamento(UUID arquivoId, int pagina, int ordem, LocalDate data, String conta,
        String documento, BigDecimal debito, BigDecimal credito) {

    public ImpressaoLancamento {
        Objects.requireNonNull(arquivoId, "arquivoId");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(debito, "debito");
        Objects.requireNonNull(credito, "credito");
    }

    public static ImpressaoLancamento de(Lancamento l) {
        return new ImpressaoLancamento(l.getArquivoId(), l.getPagina(), l.getOrdem(), l.getData(), l.getContaCodigo(),
                l.getDocumento(), l.getDebito(), l.getCredito());
    }

    public static String chave(Lancamento l) {
        return de(l).chave();
    }

    /** SHA-256 (hex, 64 caracteres) do texto canônico; dinheiro com 2 casas, vazio no lugar de nulo. */
    public String chave() {
        String canonico = String.join("|", arquivoId.toString(), Integer.toString(pagina), Integer.toString(ordem),
                data.toString(), texto(conta), texto(documento), dinheiro(debito), dinheiro(credito));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonico.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String texto(String s) {
        return s == null ? "" : s.trim();
    }

    private static String dinheiro(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString();
    }
}
