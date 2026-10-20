package br.com.condominioauditoria.api.exception;

/**
 * The storage folder refused to save an original (no write permission). Becomes 503 in the API with the message in
 * Portuguese: it is a server setup problem, not a problem with the uploaded file.
 */
public class StorageWriteDeniedException extends RuntimeException {

    public StorageWriteDeniedException(Throwable cause) {
        super("Não foi possível guardar o arquivo: a pasta de dados do servidor não aceita gravação (permissão negada)."
                + " Nada foi salvo. Avise o administrador do sistema para ajustar a permissão da pasta de dados.",
                cause);
    }
}
