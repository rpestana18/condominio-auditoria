package br.com.condominioauditoria.api.assistente;

import br.com.condominioauditoria.api.model.enums.AiMode;
import org.springframework.http.HttpStatus;

/**
 * Pergunta ou busca recusada ou que falhou, já com o status HTTP do contrato (409, 422, 429, 503, 504) e a mensagem em
 * português para a tela. modoIa só nas recusas do chat pelo modo (409). Nunca leva chave nem texto de documento.
 */
public class RecusaAssistenteException extends RuntimeException {

    private final HttpStatus status;
    private final String titulo;
    private final AiMode modoIa;

    public RecusaAssistenteException(HttpStatus status, String titulo, String mensagem, AiMode modoIa) {
        super(mensagem);
        this.status = status;
        this.titulo = titulo;
        this.modoIa = modoIa;
    }

    public HttpStatus status() {
        return status;
    }

    public String titulo() {
        return titulo;
    }

    public AiMode modoIa() {
        return modoIa;
    }
}
