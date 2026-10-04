package br.com.condominioauditoria.backend.modulo;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Um ligar ou desligar na trilha de ativação (RF-10.6). Só inclusão: a entidade é imutável e o banco recusa update e
 * delete (gatilho da migração V7).
 */
@Entity
@Immutable
public class EventoModulo {

    @Id
    private UUID id;
    private UUID condominioId;
    private String modulo;
    private boolean ligadoAntes;
    private boolean ligadoDepois;
    private String usuario;
    private Instant quando;
    private String motivo;

    protected EventoModulo() {
    }

    EventoModulo(UUID condominioId, String modulo, boolean ligadoAntes, boolean ligadoDepois, String usuario,
            Instant quando, String motivo) {
        this.id = UUID.randomUUID();
        this.condominioId = condominioId;
        this.modulo = modulo;
        this.ligadoAntes = ligadoAntes;
        this.ligadoDepois = ligadoDepois;
        this.usuario = usuario;
        this.quando = quando;
        this.motivo = motivo;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominioId() {
        return condominioId;
    }

    public String getModulo() {
        return modulo;
    }

    public boolean isLigadoAntes() {
        return ligadoAntes;
    }

    public boolean isLigadoDepois() {
        return ligadoDepois;
    }

    public String getUsuario() {
        return usuario;
    }

    public Instant getQuando() {
        return quando;
    }

    public String getMotivo() {
        return motivo;
    }
}
