package br.com.condominioauditoria.api.orcamento;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/**
 * Rubrica do catálogo do condomínio (ADR 0005, Decisão 1): linhas de exercícios diferentes ligadas à mesma rubrica
 * correspondem. Nunca é apagada; o Admin pode trocar o nome.
 */
@Entity
public class Rubrica {

    @Id
    private UUID id;
    private UUID condominioId;
    private String nome;
    private String grupoCodigo;
    private UUID linhaOrigemId;
    private String criadaPor;
    private Instant criadaEm;

    protected Rubrica() {
    }

    public Rubrica(UUID condominioId, String nome, String grupoCodigo, UUID linhaOrigemId, String usuario,
            Instant quando) {
        this.id = UUID.randomUUID();
        this.condominioId = condominioId;
        this.nome = nome;
        this.grupoCodigo = grupoCodigo;
        this.linhaOrigemId = linhaOrigemId;
        this.criadaPor = usuario;
        this.criadaEm = quando;
    }

    public void renomear(String nome) {
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

    public String getGrupoCodigo() {
        return grupoCodigo;
    }

    public UUID getLinhaOrigemId() {
        return linhaOrigemId;
    }

    public String getCriadaPor() {
        return criadaPor;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }
}
