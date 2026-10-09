package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/**
 * De-para de uma conta do fluxo numa versão da PO (RF-03.1.4): exatamente um destino por conta (chave única por PO e
 * conta). Toda mudança gera um {@link EventoDepara} na trilha, que é só de inserção.
 */
@Entity
public class DeparaConta {

    @Id
    private UUID id;
    private UUID condominioId;
    private UUID previsaoId;
    private String contaCodigo;
    private String contaNome;
    @Enumerated(EnumType.STRING)
    private TipoDestino tipoDestino;
    private UUID linhaPoId;
    private String detalheDestino;
    @Enumerated(EnumType.STRING)
    private EstadoDepara estado;
    @Enumerated(EnumType.STRING)
    private OrigemDepara origem;
    private String motivo;
    private boolean igualVersaoAnterior;
    private String atualizadoPor;
    private Instant atualizadoEm;

    protected DeparaConta() {
    }

    public DeparaConta(Budget previsao, String contaCodigo, String contaNome, Destino destino,
            EstadoDepara estado, OrigemDepara origem, String motivo, boolean igualVersaoAnterior, String usuario,
            Instant quando) {
        this.id = UUID.randomUUID();
        this.condominioId = previsao.getCondominiumId();
        this.previsaoId = previsao.getId();
        this.contaCodigo = contaCodigo;
        this.contaNome = contaNome;
        alterar(destino, estado, origem, motivo, igualVersaoAnterior, usuario, quando);
    }

    public void alterar(Destino destino, EstadoDepara estado, OrigemDepara origem, String motivo,
            boolean igualVersaoAnterior, String usuario, Instant quando) {
        this.tipoDestino = destino.tipo();
        this.linhaPoId = destino.linhaPoId();
        this.detalheDestino = destino.detalhe();
        this.estado = estado;
        this.origem = origem;
        this.motivo = motivo;
        this.igualVersaoAnterior = igualVersaoAnterior;
        this.atualizadoPor = usuario;
        this.atualizadoEm = quando;
    }

    /** Só o estado muda (confirmar ou recusar); destino, origem e motivo continuam. */
    public void mudarEstado(EstadoDepara novo, String usuario, Instant quando) {
        this.estado = novo;
        this.atualizadoPor = usuario;
        this.atualizadoEm = quando;
    }

    public void atualizarNome(String nome) {
        if (nome != null && !nome.isBlank()) {
            this.contaNome = nome;
        }
    }

    /** Destino sem o texto da linha (para o texto, use {@link #destino(java.util.Map)}). */
    public Destino destino() {
        return new Destino(tipoDestino, linhaPoId, null, null, detalheDestino);
    }

    public Destino destino(java.util.Map<UUID, BudgetLine> linhas) {
        BudgetLine l = linhaPoId == null ? null : linhas.get(linhaPoId);
        return l == null ? destino() : new Destino(tipoDestino, linhaPoId, l.getEffectiveCode(), l.getDescription(), null);
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

    public String getContaCodigo() {
        return contaCodigo;
    }

    public String getContaNome() {
        return contaNome;
    }

    public TipoDestino getTipoDestino() {
        return tipoDestino;
    }

    public UUID getLinhaPoId() {
        return linhaPoId;
    }

    public String getDetalheDestino() {
        return detalheDestino;
    }

    public EstadoDepara getEstado() {
        return estado;
    }

    public OrigemDepara getOrigem() {
        return origem;
    }

    public String getMotivo() {
        return motivo;
    }

    public boolean isIgualVersaoAnterior() {
        return igualVersaoAnterior;
    }

    public String getAtualizadoPor() {
        return atualizadoPor;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
