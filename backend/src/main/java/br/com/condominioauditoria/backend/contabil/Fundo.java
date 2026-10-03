package br.com.condominioauditoria.backend.contabil;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/** Fundo/subconta do condomínio (ordinária, reserva, obras...). Criado na primeira vez que aparece num relatório. */
@Entity
public class Fundo {

    @Id
    private UUID id;
    private UUID condominioId;
    private String nome;

    protected Fundo() {
    }

    public Fundo(UUID condominioId, String nome) {
        this.id = UUID.randomUUID();
        this.condominioId = condominioId;
        this.nome = nome;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominioId() {
        return condominioId;
    }

    public String getNome() {
        return nome;
    }
}
