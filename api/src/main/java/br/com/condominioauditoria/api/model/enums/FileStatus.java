package br.com.condominioauditoria.api.model.enums;

/** Processing life cycle of an uploaded file. */
public enum FileStatus {
    /** Received and waiting for the queue. */
    PENDING,
    /** Being read and saved. */
    PROCESSING,
    /** Data saved and every totals check passed. */
    COMPLETED,
    /** Data saved, but a totals check failed: it needs a human look. */
    NEEDS_REVIEW,
    /** Nothing was saved (rollback). The error message gives the reason. */
    FAILED
}
