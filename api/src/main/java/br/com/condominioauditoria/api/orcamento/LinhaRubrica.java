package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.model.budget.BudgetLine;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/**
 * Rubrica de uma linha de PO confirmada (RF-11.7): exatamente uma por linha. Toda mudança gera um
 * {@link EventoRubrica} na trilha, que é só de inserção.
 */
@Entity
public class LinhaRubrica {

    @Id
    private UUID id;
    private UUID condominioId;
    private UUID previsaoId;
    private UUID linhaPoId;
    private UUID rubricaId;
    @Enumerated(EnumType.STRING)
    private EstadoRubrica estado;
    @Enumerated(EnumType.STRING)
    private OrigemRubrica origem;
    private String motivo;
    private String atualizadoPor;
    private Instant atualizadoEm;

    protected LinhaRubrica() {
    }

    public LinhaRubrica(BudgetLine linha, Rubrica rubrica, EstadoRubrica estado, OrigemRubrica origem, String motivo,
            String usuario, Instant quando) {
        this.id = UUID.randomUUID();
        this.condominioId = linha.getCondominiumId();
        this.previsaoId = linha.getBudgetId();
        this.linhaPoId = linha.getId();
        alterar(rubrica, estado, origem, motivo, usuario, quando);
    }

    public void alterar(Rubrica rubrica, EstadoRubrica estado, OrigemRubrica origem, String motivo, String usuario,
            Instant quando) {
        this.rubricaId = rubrica.getId();
        this.estado = estado;
        this.origem = origem;
        this.motivo = motivo;
        this.atualizadoPor = usuario;
        this.atualizadoEm = quando;
    }

    /** Só o estado muda (confirmar ou recusar); rubrica, origem e motivo continuam. */
    public void mudarEstado(EstadoRubrica novo, String usuario, Instant quando) {
        this.estado = novo;
        this.atualizadoPor = usuario;
        this.atualizadoEm = quando;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominioId() {
        return condominioId;
    }

    public UUID getPrevisaoId() {
        return previsaoId;
    }

    public UUID getLinhaPoId() {
        return linhaPoId;
    }

    public UUID getRubricaId() {
        return rubricaId;
    }

    public EstadoRubrica getEstado() {
        return estado;
    }

    public OrigemRubrica getOrigem() {
        return origem;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getAtualizadoPor() {
        return atualizadoPor;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
