package br.com.condominioauditoria.backend.modulo;

/** Código que não está no catálogo de módulos (404 na API). */
public class ModuloDesconhecidoException extends RuntimeException {

    public ModuloDesconhecidoException(String codigo) {
        super("Módulo não existe no catálogo: " + codigo);
    }
}
