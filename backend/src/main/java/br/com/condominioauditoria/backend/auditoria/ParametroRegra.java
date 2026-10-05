package br.com.condominioauditoria.backend.auditoria;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Parâmetro de regra por condomínio, com vigência e fonte (ex.: teto do fundo de reserva da Conv. 20.1). */
@Entity
public class ParametroRegra {

    @Id
    private UUID id;
    private UUID condominioId;
    private String codigo;
    private BigDecimal valor;
    private LocalDate vigenteDesde;
    private LocalDate vigenteAte;
    private String fonte;

    protected ParametroRegra() {
    }

    public ParametroRegra(UUID condominioId, String codigo, BigDecimal valor, LocalDate vigenteDesde,
            LocalDate vigenteAte, String fonte) {
        this.id = UUID.randomUUID();
        this.condominioId = condominioId;
        this.codigo = codigo;
        this.valor = valor;
        this.vigenteDesde = vigenteDesde;
        this.vigenteAte = vigenteAte;
        this.fonte = fonte;
    }

    public String getCodigo() {
        return codigo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public LocalDate getVigenteDesde() {
        return vigenteDesde;
    }

    public LocalDate getVigenteAte() {
        return vigenteAte;
    }

    public String getFonte() {
        return fonte;
    }
}
