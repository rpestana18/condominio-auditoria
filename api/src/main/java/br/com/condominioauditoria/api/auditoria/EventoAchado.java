package br.com.condominioauditoria.api.auditoria;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/** Histórico do achado (só de inserção, o banco recusa update e delete): estado, condição, motivo, quem e quando. */
@Entity
public class EventoAchado {

    @Id
    private UUID id;
    private UUID achadoId;
    private UUID condominioId;
    @Enumerated(EnumType.STRING)
    private EstadoAchado estadoAnterior;
    @Enumerated(EnumType.STRING)
    private EstadoAchado estadoNovo;
    private boolean condicaoPresente;
    private String motivo;
    private String usuario;
    private Instant em;

    protected EventoAchado() {
    }

    public EventoAchado(Achado achado, EstadoAchado estadoAnterior, String motivo, String usuario, Instant em) {
        this.id = UUID.randomUUID();
        this.achadoId = achado.getId();
        this.condominioId = achado.getCondominioId();
        this.estadoAnterior = estadoAnterior;
        this.estadoNovo = achado.getEstado();
        this.condicaoPresente = achado.isCondicaoPresente();
        this.motivo = motivo;
        this.usuario = usuario;
        this.em = em;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAchadoId() {
        return achadoId;
    }

    public EstadoAchado getEstadoAnterior() {
        return estadoAnterior;
    }

    public EstadoAchado getEstadoNovo() {
        return estadoNovo;
    }

    public boolean isCondicaoPresente() {
        return condicaoPresente;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getUsuario() {
        return usuario;
    }

    public Instant getEm() {
        return em;
    }
}
