package br.com.condominioauditoria.backend.condominio;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

@Entity
public class Condominio {

    @Id
    private UUID id;
    private String nome;
    private String cnpj;
    private Instant criadoEm;

    protected Condominio() {
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getCnpj() {
        return cnpj;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
