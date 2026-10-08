package br.com.condominioauditoria.backend.orcamento;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/**
 * Trilha das rubricas (RF-11.7): quem, quando, linha, rubrica e estado anteriores e novos. Só de inserção: o banco
 * recusa update e delete por gatilho (V14). Criação e troca de nome da rubrica não têm linha.
 */
@Entity
public class EventoRubrica {

    /** Ação registrada: rubrica criada ou renomeada; sugestão, confirmação, recusa ou troca de rubrica da linha. */
    public enum Acao {
        CRIADA, RENOMEADA, SUGERIDO, CONFIRMADO, RECUSADO, ALTERADO
    }

    @Id
    private UUID id;
    private UUID condominioId;
    private UUID previsaoId;
    private UUID linhaPoId;
    private String linhaCodigo;
    private String linhaDescricao;
    @Enumerated(EnumType.STRING)
    private Acao acao;
    private String usuario;
    private Instant em;
    private UUID rubricaAnteriorId;
    private String rubricaAnterior;
    @Enumerated(EnumType.STRING)
    private EstadoRubrica estadoAnterior;
    private UUID rubricaNovaId;
    private String rubricaNova;
    @Enumerated(EnumType.STRING)
    private EstadoRubrica estadoNovo;
    @Enumerated(EnumType.STRING)
    private OrigemRubrica origem;
    private String motivo;

    protected EventoRubrica() {
    }

    /**
     * Evento de uma linha.
     *
     * @param anterior rubrica antes da mudança (nula quando a linha ainda não tinha rubrica)
     * @param estadoAnterior estado antes da mudança (nulo quando a linha ainda não tinha rubrica)
     */
    public static EventoRubrica daLinha(LinhaPo linha, LinhaRubrica atual, Acao acao, Rubrica anterior,
            EstadoRubrica estadoAnterior, Rubrica nova, String usuario, Instant em) {
        EventoRubrica e = new EventoRubrica(linha.getCondominioId(), acao, nova, usuario, em);
        e.previsaoId = linha.getPrevisaoId();
        e.linhaPoId = linha.getId();
        e.linhaCodigo = linha.getCodigoEfetivo();
        e.linhaDescricao = linha.getDescricao();
        if (anterior != null) {
            e.rubricaAnteriorId = anterior.getId();
            e.rubricaAnterior = anterior.getNome();
        }
        e.estadoAnterior = estadoAnterior;
        e.estadoNovo = atual.getEstado();
        e.origem = atual.getOrigem();
        e.motivo = atual.getMotivo();
        return e;
    }

    /** Rubrica criada (sem linha, ou a partir de uma linha da PO). */
    public static EventoRubrica criada(Rubrica rubrica, LinhaPo origem, String usuario, Instant em) {
        EventoRubrica e = new EventoRubrica(rubrica.getCondominioId(), Acao.CRIADA, rubrica, usuario, em);
        if (origem != null) {
            e.previsaoId = origem.getPrevisaoId();
            e.linhaPoId = origem.getId();
            e.linhaCodigo = origem.getCodigoEfetivo();
            e.linhaDescricao = origem.getDescricao();
        }
        return e;
    }

    public static EventoRubrica renomeada(Rubrica rubrica, String nomeAnterior, String usuario, Instant em) {
        EventoRubrica e = new EventoRubrica(rubrica.getCondominioId(), Acao.RENOMEADA, rubrica, usuario, em);
        e.rubricaAnteriorId = rubrica.getId();
        e.rubricaAnterior = nomeAnterior;
        return e;
    }

    private EventoRubrica(UUID condominioId, Acao acao, Rubrica nova, String usuario, Instant em) {
        this.id = UUID.randomUUID();
        this.condominioId = condominioId;
        this.acao = acao;
        this.usuario = usuario;
        this.em = em;
        this.rubricaNovaId = nova.getId();
        this.rubricaNova = nova.getNome();
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

    public String getLinhaCodigo() {
        return linhaCodigo;
    }

    public String getLinhaDescricao() {
        return linhaDescricao;
    }

    public Acao getAcao() {
        return acao;
    }

    public String getUsuario() {
        return usuario;
    }

    public Instant getEm() {
        return em;
    }

    public UUID getRubricaAnteriorId() {
        return rubricaAnteriorId;
    }

    public String getRubricaAnterior() {
        return rubricaAnterior;
    }

    public EstadoRubrica getEstadoAnterior() {
        return estadoAnterior;
    }

    public UUID getRubricaNovaId() {
        return rubricaNovaId;
    }

    public String getRubricaNova() {
        return rubricaNova;
    }

    public EstadoRubrica getEstadoNovo() {
        return estadoNovo;
    }

    public OrigemRubrica getOrigem() {
        return origem;
    }

    public String getMotivo() {
        return motivo;
    }
}
