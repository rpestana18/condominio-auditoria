package br.com.condominioauditoria.api.orcamento;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/**
 * Trilha do de-para (RF-03.1.4): quem, quando, conta, destino e estado anteriores e novos. Só de inserção: o banco
 * recusa update e delete por gatilho (V8). Migra para a trilha geral quando o RF-07.4 existir.
 */
@Entity
public class EventoDepara {

    /** Ação registrada: sugestão criada, confirmação, recusa ou troca de destino. */
    public enum Acao {
        SUGERIDO, CONFIRMADO, RECUSADO, ALTERADO
    }

    @Id
    private UUID id;
    private UUID condominioId;
    private UUID previsaoId;
    private String contaCodigo;
    private String contaNome;
    @Enumerated(EnumType.STRING)
    private Acao acao;
    private String usuario;
    private Instant em;
    @Enumerated(EnumType.STRING)
    private TipoDestino tipoDestinoAnterior;
    private UUID linhaPoAnteriorId;
    private String destinoAnterior;
    @Enumerated(EnumType.STRING)
    private EstadoDepara estadoAnterior;
    @Enumerated(EnumType.STRING)
    private TipoDestino tipoDestinoNovo;
    private UUID linhaPoNovaId;
    private String destinoNovo;
    @Enumerated(EnumType.STRING)
    private EstadoDepara estadoNovo;
    @Enumerated(EnumType.STRING)
    private OrigemDepara origem;
    private String motivo;

    protected EventoDepara() {
    }

    /**
     * @param anterior destino antes da mudança (nulo quando a conta ainda não tinha de-para)
     * @param estadoAnterior estado antes da mudança (nulo quando a conta ainda não tinha de-para)
     */
    public EventoDepara(DeparaConta depara, Acao acao, Destino anterior, EstadoDepara estadoAnterior, Destino novo,
            String usuario, Instant em) {
        this.id = UUID.randomUUID();
        this.condominioId = depara.getCondominioId();
        this.previsaoId = depara.getPrevisaoId();
        this.contaCodigo = depara.getContaCodigo();
        this.contaNome = depara.getContaNome();
        this.acao = acao;
        this.usuario = usuario;
        this.em = em;
        if (anterior != null) {
            this.tipoDestinoAnterior = anterior.tipo();
            this.linhaPoAnteriorId = anterior.linhaPoId();
            this.destinoAnterior = anterior.texto();
        }
        this.estadoAnterior = estadoAnterior;
        this.tipoDestinoNovo = novo.tipo();
        this.linhaPoNovaId = novo.linhaPoId();
        this.destinoNovo = novo.texto();
        this.estadoNovo = depara.getEstado();
        this.origem = depara.getOrigem();
        this.motivo = depara.getMotivo();
    }

    public UUID getId() {
        return id;
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

    public Acao getAcao() {
        return acao;
    }

    public String getUsuario() {
        return usuario;
    }

    public Instant getEm() {
        return em;
    }

    public TipoDestino getTipoDestinoAnterior() {
        return tipoDestinoAnterior;
    }

    public UUID getLinhaPoAnteriorId() {
        return linhaPoAnteriorId;
    }

    public String getDestinoAnterior() {
        return destinoAnterior;
    }

    public EstadoDepara getEstadoAnterior() {
        return estadoAnterior;
    }

    public TipoDestino getTipoDestinoNovo() {
        return tipoDestinoNovo;
    }

    public UUID getLinhaPoNovaId() {
        return linhaPoNovaId;
    }

    public String getDestinoNovo() {
        return destinoNovo;
    }

    public EstadoDepara getEstadoNovo() {
        return estadoNovo;
    }

    public OrigemDepara getOrigem() {
        return origem;
    }

    public String getMotivo() {
        return motivo;
    }
}
