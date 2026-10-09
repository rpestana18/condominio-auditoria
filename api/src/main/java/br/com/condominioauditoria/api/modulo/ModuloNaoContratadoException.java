package br.com.condominioauditoria.api.modulo;

/**
 * Recusa de qualquer ponto de um módulo desligado no condomínio (RF-10.3). Na API REST vira 403 com o código do
 * módulo; no gRPC vira FAILED_PRECONDITION com a mesma mensagem (contracts/grpc/consulta/v1).
 */
public class ModuloNaoContratadoException extends RuntimeException {

    private final String modulo;

    public ModuloNaoContratadoException(String modulo, String nome) {
        super("Módulo " + nome + " não contratado para este condomínio.");
        this.modulo = modulo;
    }

    public String modulo() {
        return modulo;
    }
}
