package br.com.condominioauditoria.api.exception;

/**
 * Rejection of any entry point of a feature disabled in the condominium (RF-10.3). In the REST API it becomes 403 with
 * the feature code; in gRPC it becomes FAILED_PRECONDITION with the same message (contracts/grpc/query/v2).
 */
public class FeatureNotEnabledException extends RuntimeException {

    private final String feature;

    public FeatureNotEnabledException(String feature, String name) {
        super("Módulo " + name + " não contratado para este condomínio.");
        this.feature = feature;
    }

    public String feature() {
        return feature;
    }
}
