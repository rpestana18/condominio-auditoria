package br.com.condominioauditoria.api.model.accounting;

import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.TotalsCheckData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Saved result of an arithmetic totals check of the file. */
@Entity
@Table(name = "conferencia")
public class TotalsCheck {

    @Id
    private UUID id;
    @Column(name = "arquivo_id")
    private UUID fileId;
    @Column(name = "ordem")
    private int sequence;
    @Column(name = "codigo")
    private String code;
    @Column(name = "descricao")
    private String description;
    private boolean ok;
    @Column(name = "detalhe")
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
