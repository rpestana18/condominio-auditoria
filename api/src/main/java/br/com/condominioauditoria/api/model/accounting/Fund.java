package br.com.condominioauditoria.api.model.accounting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Fund/sub-account of the condominium (operating, reserve, works...). Created the first time it appears in a report.
 */
@Entity
@Table(name = "fundo")
public class Fund {

    @Id
    private UUID id;
    @Column(name = "condominio_id")
    private UUID condominiumId;
    @Column(name = "nome")
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
