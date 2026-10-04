package br.com.condominioauditoria.backend.orcamento;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/** Trilha da realocação (só de inserção: o banco recusa update e delete). */
@Entity
public class EventoRealocacao {

    public static final String REALOCADA = "REALOCADA";
    public static final String DESFEITA = "DESFEITA";

    @Id
    private UUID id;
    private UUID realocacaoId;
    private UUID condominioId;
    private String acao;
    private String usuario;
    private Instant em;
    private String detalhe;

    protected EventoRealocacao() {
    }

    public EventoRealocacao(RealocacaoLancamento r, String acao, String usuario, Instant em, String detalhe) {
        this.id = UUID.randomUUID();
        this.realocacaoId = r.getId();
        this.condominioId = r.getCondominioId();
        this.acao = acao;
        this.usuario = usuario;
        this.em = em;
        this.detalhe = detalhe;
    }

    public UUID getRealocacaoId() {
        return realocacaoId;
    }

    public String getAcao() {
        return acao;
    }

    public String getUsuario() {
        return usuario;
    }

    public Instant getEm() {
        return em;
    }

    public String getDetalhe() {
        return detalhe;
    }
}
