package br.com.condominioauditoria.api.arquivo;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Registro de uma troca de categoria (RF-01.7): quem, quando, a anterior e a nova. */
@Entity
@Table(name = "arquivo_categoria_historico")
public class HistoricoCategoria {

    @Id
    private UUID id;
    private UUID arquivoId;
    @Enumerated(EnumType.STRING)
    private Categoria categoriaAnterior;
    @Enumerated(EnumType.STRING)
    private Categoria categoriaNova;
    private String alteradoPor;
    private Instant alteradoEm;

    protected HistoricoCategoria() {
    }

    HistoricoCategoria(UUID arquivoId, Categoria categoriaAnterior, Categoria categoriaNova, String alteradoPor) {
        this.id = UUID.randomUUID();
        this.arquivoId = arquivoId;
        this.categoriaAnterior = categoriaAnterior;
        this.categoriaNova = categoriaNova;
        this.alteradoPor = alteradoPor;
        this.alteradoEm = Instant.now();
    }

    public UUID getArquivoId() {
        return arquivoId;
    }

    public Categoria getCategoriaAnterior() {
        return categoriaAnterior;
    }

    public Categoria getCategoriaNova() {
        return categoriaNova;
    }

    public String getAlteradoPor() {
        return alteradoPor;
    }

    public Instant getAlteradoEm() {
        return alteradoEm;
    }
}
