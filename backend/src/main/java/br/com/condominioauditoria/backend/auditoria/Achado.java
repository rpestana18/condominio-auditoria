package br.com.condominioauditoria.backend.auditoria;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Indício apontado por uma regra, com a versão da regra e a evidência (arquivo, página e hash). Único por condomínio,
 * regra, competência e alvo: recalcular nunca duplica. O texto descreve o fato e o que verificar, nunca uma causa.
 */
@Entity
public class Achado {

    @Id
    private UUID id;
    private UUID condominioId;
    private String regra;
    private String versaoRegra;
    @Enumerated(EnumType.STRING)
    private Severidade severidade;
    private LocalDate competencia;
    private String alvo;
    private String descricao;
    @Enumerated(EnumType.STRING)
    private EstadoAchado estado;
    private Instant criadoEm;

    protected Achado() {
    }

    public Achado(UUID condominioId, String regra, String versaoRegra, Severidade severidade, YearMonth competencia,
            String alvo, String descricao, Instant criadoEm) {
        this.id = UUID.randomUUID();
        this.condominioId = condominioId;
        this.regra = regra;
        this.versaoRegra = versaoRegra;
        this.severidade = severidade;
        this.competencia = competencia.atDay(1);
        this.alvo = alvo;
        this.descricao = descricao;
        this.estado = EstadoAchado.ABERTO;
        this.criadoEm = criadoEm;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominioId() {
        return condominioId;
    }

    public String getRegra() {
        return regra;
    }

    public String getVersaoRegra() {
        return versaoRegra;
    }

    public Severidade getSeveridade() {
        return severidade;
    }

    public YearMonth getCompetencia() {
        return YearMonth.from(competencia);
    }

    public String getAlvo() {
        return alvo;
    }

    public String getDescricao() {
        return descricao;
    }

    public EstadoAchado getEstado() {
        return estado;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
