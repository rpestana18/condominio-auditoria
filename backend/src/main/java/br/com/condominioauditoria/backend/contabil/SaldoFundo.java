package br.com.condominioauditoria.backend.contabil;

import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.Posicao;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Posição de um fundo no período de um relatório (linha da Posição Financeira). */
@Entity
public class SaldoFundo {

    @Id
    private UUID id;
    private UUID condominioId;
    private UUID arquivoId;
    private UUID fundoId;
    private LocalDate periodoInicio;
    private LocalDate periodoFim;
    private BigDecimal saldoAnterior;
    private BigDecimal creditos;
    private BigDecimal debitos;
    private BigDecimal saldoAtual;

    protected SaldoFundo() {
    }

    public SaldoFundo(UUID condominioId, UUID arquivoId, UUID fundoId, LocalDate inicio, LocalDate fim, Posicao p) {
        this.id = UUID.randomUUID();
        this.condominioId = condominioId;
        this.arquivoId = arquivoId;
        this.fundoId = fundoId;
        this.periodoInicio = inicio;
        this.periodoFim = fim;
        this.saldoAnterior = p.saldoAnterior();
        this.creditos = p.creditos();
        this.debitos = p.debitos();
        this.saldoAtual = p.saldoAtual();
    }

    public UUID getFundoId() {
        return fundoId;
    }

    public LocalDate getPeriodoInicio() {
        return periodoInicio;
    }

    public LocalDate getPeriodoFim() {
        return periodoFim;
    }

    public BigDecimal getSaldoAnterior() {
        return saldoAnterior;
    }

    public BigDecimal getCreditos() {
        return creditos;
    }

    public BigDecimal getDebitos() {
        return debitos;
    }

    public BigDecimal getSaldoAtual() {
        return saldoAtual;
    }
}
