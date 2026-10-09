package br.com.condominioauditoria.api.model.enums;

/** Processing life cycle of an uploaded file. */
public enum FileStatus {
    /** Received and waiting for the queue. */
    PENDENTE,
    /** Being read and saved. */
    PROCESSANDO,
    /** Data saved and every totals check passed. */
    CONCLUIDO,
    /** Data saved, but a totals check failed: it needs a human look. */
    PRECISA_REVISAO,
    /** Nothing was saved (rollback). The error message gives the reason. */
    FALHOU
}
