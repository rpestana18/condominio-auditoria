package br.com.condominioauditoria.backend.orcamento;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/** Trilha da PO: quem, quando, o quê. Só de inserção (o banco recusa update e delete). */
@Entity
public class EventoPrevisao {

    public static final String CONFIRMADA = "CONFIRMADA";
    public static final String SUBSTITUIDA = "SUBSTITUIDA";
    /** Ligação das linhas 1.9.x aos fundos alterada depois da confirmação (RF-03.1.9). */
    public static final String FUNDOS_ALTERADOS = "FUNDOS_ALTERADOS";
    /** PO marcada como prorrogada pelo Admin, com justificativa (RF-11.3). */
    public static final String PRORROGADA = "PRORROGADA";
    /** Prorrogação desfeita pelo Admin. */
    public static final String PRORROGACAO_DESFEITA = "PRORROGACAO_DESFEITA";
    /** Prorrogação encurtada (ou desfeita) sozinha porque outra PO foi confirmada nos meses prorrogados (RF-11.3). */
    public static final String PRORROGACAO_ENCURTADA = "PRORROGACAO_ENCURTADA";

    @Id
    private UUID id;
    private UUID previsaoId;
    private UUID condominioId;
    private String tipo;
    private String usuario;
    private Instant em;
    private String justificativa;
    private String detalhe;

    protected EventoPrevisao() {
    }

    public EventoPrevisao(PrevisaoOrcamentaria previsao, String tipo, String usuario, Instant em, String justificativa,
            String detalhe) {
        this.id = UUID.randomUUID();
        this.previsaoId = previsao.getId();
        this.condominioId = previsao.getCondominioId();
        this.tipo = tipo;
        this.usuario = usuario;
        this.em = em;
        this.justificativa = justificativa;
        this.detalhe = detalhe;
    }

    public UUID getPrevisaoId() {
        return previsaoId;
    }

    public String getTipo() {
        return tipo;
    }

    public String getUsuario() {
        return usuario;
    }

    public Instant getEm() {
        return em;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public String getDetalhe() {
        return detalhe;
    }
}
