package br.com.condominioauditoria.api.model.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Rule parameter per condominium, with validity and source (e.g. reserve fund cap of Conv. 20.1). */
@Entity
@Table(name = "parametro_regra")
public class RuleParameter {

    @Id
    private UUID id;
    @Column(name = "condominio_id")
    private UUID condominiumId;
    @Column(name = "codigo")
    private String code;
    @Column(name = "valor")
    private BigDecimal value;
    @Column(name = "vigente_desde")
    private LocalDate validFrom;
    @Column(name = "vigente_ate")
    private LocalDate validTo;
    @Column(name = "fonte")
    private String source;

    protected RuleParameter() {
    }

    public RuleParameter(UUID condominiumId, String code, BigDecimal value, LocalDate validFrom,
            LocalDate validTo, String font) {
        this.id = UUID.randomUUID();
        this.condominiumId = condominiumId;
        this.code = code;
        this.value = value;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.source = font;
    }

    public String getCode() {
        return code;
    }

    public BigDecimal getValue() {
        return value;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public String getSource() {
        return source;
    }
}
