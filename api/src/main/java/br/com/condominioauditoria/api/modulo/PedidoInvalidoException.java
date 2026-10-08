package br.com.condominioauditoria.api.modulo;

/** Pedido com dado faltando ou incoerente (motivo vazio, período invertido). Vira 400 na API. */
public class PedidoInvalidoException extends RuntimeException {

    public PedidoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
