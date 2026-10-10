package br.com.condominioauditoria.api.model.accounting;

import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.TotalsCheckData;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/** Saved result of an arithmetic totals check of the file. */
@Entity
public class TotalsCheck {

    @Id
    private UUID id;
    private UUID fileId;
    private int sequence;
    private String code;
    private String description;
    private boolean ok;
    private String detail;

    protected TotalsCheck() {
    }

    public TotalsCheck(UUID fileId, int sequence, TotalsCheckData v) {
        this.id = UUID.randomUUID();
        this.fileId = fileId;
        this.sequence = sequence;
        this.code = v.code();
        this.description = v.description();
        this.ok = v.ok();
        this.detail = v.detail();
    }

    public UUID getFileId() {
        return fileId;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean isOk() {
        return ok;
    }

    public String getDetail() {
        return detail;
    }
}
