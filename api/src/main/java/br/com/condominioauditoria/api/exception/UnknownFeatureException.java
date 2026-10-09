package br.com.condominioauditoria.api.exception;

/** Code that is not in the feature catalog (404 in the API). */
public class UnknownFeatureException extends RuntimeException {

    public UnknownFeatureException(String code) {
        super("Módulo não existe no catálogo: " + code);
    }
}
