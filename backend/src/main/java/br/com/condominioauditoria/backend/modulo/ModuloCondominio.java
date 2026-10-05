package br.com.condominioauditoria.backend.modulo;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Estado atual de um módulo num condomínio. Sem linha = vale o padrão do catálogo. O histórico fica em EventoModulo. */
@Entity
public class ModuloCondominio {

    @EmbeddedId
    private Chave chave;
    private boolean ligado;
    private Instant desde;
    private String alteradoPor;

    protected ModuloCondominio() {
    }

    ModuloCondominio(UUID condominioId, String modulo, boolean ligado, Instant desde, String alteradoPor) {
        this.chave = new Chave(condominioId, modulo);
        this.ligado = ligado;
        this.desde = desde;
        this.alteradoPor = alteradoPor;
    }

    void alterar(boolean ligado, Instant quando, String usuario) {
        this.ligado = ligado;
        this.desde = quando;
        this.alteradoPor = usuario;
    }

    public UUID getCondominioId() {
        return chave.condominioId;
    }

    public String getModulo() {
        return chave.modulo;
    }

    public boolean isLigado() {
        return ligado;
    }

    public Instant getDesde() {
        return desde;
    }

    public String getAlteradoPor() {
        return alteradoPor;
    }

    @Embeddable
    public static class Chave implements Serializable {

        private UUID condominioId;
        private String modulo;

        protected Chave() {
        }

        public Chave(UUID condominioId, String modulo) {
            this.condominioId = condominioId;
            this.modulo = modulo;
        }

        @Override
        public boolean equals(Object outro) {
            return outro instanceof Chave c && Objects.equals(condominioId, c.condominioId)
                    && Objects.equals(modulo, c.modulo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(condominioId, modulo);
        }
    }
}
