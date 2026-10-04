package br.com.condominioauditoria.backend.orcamento;

import java.util.List;
import org.springframework.http.HttpStatus;

/** Confirmação da PO recusada, com todos os motivos de uma vez (422) ou por conflito com outra PO (409). */
public class ConfirmacaoRecusadaException extends RuntimeException {

    private final HttpStatus status;
    private final List<String> motivos;

    public ConfirmacaoRecusadaException(HttpStatus status, List<String> motivos) {
        super(String.join(" ", motivos));
        this.status = status;
        this.motivos = List.copyOf(motivos);
    }

    public HttpStatus status() {
        return status;
    }

    public List<String> motivos() {
        return motivos;
    }
}
