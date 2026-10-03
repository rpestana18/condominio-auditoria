package br.com.condominioauditoria.ingestao.fluxo;

import br.com.condominioauditoria.ingestao.contrato.DocumentoLido.Palavra;
import java.util.Optional;

/**
 * Posição das colunas, tirada do cabeçalho "DATA CONTA CONTÁBIL CÓDIGO HISTÓRICO CRÉDITO DÉBITO SALDO".
 * Cada fundo tem o próprio cabeçalho, e as posições mudam um pouco de um para outro.
 * Colunas de texto começam no x0 do título; colunas de valor são alinhadas à direita, pelo x1.
 */
record Colunas(double conta, double codigo, double historico, double creditoDireita, double debitoDireita, double saldoDireita) {

    /** Distância máxima entre o fim de um número e o fim do título da coluna. */
    private static final double TOLERANCIA_VALOR = 15;

    static Optional<Colunas> doCabecalho(Linha linha) {
        if (!linha.contem("DATA") || !linha.contem("HISTÓRICO") || !linha.contem("CRÉDITO")) {
            return Optional.empty();
        }
        // Em alguns fundos "CONTA CONTÁBIL" quebra em duas linhas, fora da linha do cabeçalho.
        // A coluna de conta começa logo depois da data.
        double conta = linha.contem("CONTA") ? palavra(linha, "CONTA").x0() : palavra(linha, "DATA").x1() + 5;
        return Optional.of(new Colunas(
                conta,
                palavra(linha, "CÓDIGO").x0(),
                palavra(linha, "HISTÓRICO").x0(),
                palavra(linha, "CRÉDITO").x1(),
                palavra(linha, "DÉBITO").x1(),
                palavra(linha, "SALDO").x1()));
    }

    private static Palavra palavra(Linha linha, String texto) {
        return linha.palavras().stream().filter(p -> p.texto().equals(texto)).findFirst()
                .orElseThrow(() -> new InterpretadorFluxoCaixa.LeituraFluxoException(
                        "Cabeçalho sem a coluna " + texto + " na pág. " + linha.pagina()));
    }

    enum Valor { CREDITO, DEBITO, SALDO }

    /** Em qual coluna de valor o número cai, ou vazio se não estiver alinhado com nenhuma. */
    Optional<Valor> colunaDeValor(Palavra p) {
        double c = Math.abs(p.x1() - creditoDireita);
        double d = Math.abs(p.x1() - debitoDireita);
        double s = Math.abs(p.x1() - saldoDireita);
        double menor = Math.min(c, Math.min(d, s));
        if (menor > TOLERANCIA_VALOR) {
            return Optional.empty();
        }
        return Optional.of(menor == c ? Valor.CREDITO : menor == d ? Valor.DEBITO : Valor.SALDO);
    }

    enum Texto { CONTA, CODIGO, HISTORICO }

    Optional<Texto> colunaDeTexto(Palavra p) {
        double x = p.x0() + 2;
        if (x >= historico) {
            return Optional.of(Texto.HISTORICO);
        }
        if (x >= codigo) {
            return Optional.of(Texto.CODIGO);
        }
        if (x >= conta) {
            return Optional.of(Texto.CONTA);
        }
        return Optional.empty();
    }
}
