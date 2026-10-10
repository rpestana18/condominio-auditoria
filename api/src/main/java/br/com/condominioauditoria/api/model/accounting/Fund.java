package br.com.condominioauditoria.api.model.accounting;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/**
 * Fund/sub-account of the condominium (operating, reserve, works...). Created the first time it appears in a report.
 */
@Entity
public class Fund {

    @Id
    private UUID id;
    private UUID condominiumId;
    private String name;

    protected Fund() {
    }

    public Fund(UUID condominiumId, String name) {
        this.id = UUID.randomUUID();
        this.condominiumId = condominiumId;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public String getName() {
        return name;
    }
}
