package br.com.condominioauditoria.api.model.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Rule parameter per condominium, with validity and source (e.g. reserve fund cap of Conv. 20.1). */
@Entity
public class RuleParameter {

    @Id
    private UUID id;
    private UUID condominiumId;
    private String code;
    private BigDecimal value;
    private LocalDate validFrom;
    private LocalDate validTo;
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
