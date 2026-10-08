package br.com.condominioauditoria.backend.orcamento;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * PO lida de um arquivo da categoria PO (ADR 0004, Decisão 3). Uma por arquivo: reprocessar atualiza esta mesma
 * linha enquanto a PO não foi confirmada. Arquivo e hash de origem ficam aqui e em cada linha.
 */
@Entity
public class PrevisaoOrcamentaria {

    @Id
    private UUID id;
    private UUID condominioId;
    private UUID arquivoId;
    private String sha256;
    private String interpretador;
    private String titulo;
    private String exercicioImpresso;
    private String colunaOrcadoAnterior;
    private String colunaOrcado;
    @Enumerated(EnumType.STRING)
    private EstadoPrevisao estado;
    private BigDecimal totalImpresso;
    private BigDecimal previstoMesImpresso;
    private BigDecimal previstoMes;
    private BigDecimal toleranciaArredondamento;
    private Instant lidaEm;
    private Integer versao;
    private LocalDate exercicioInicio;
    private LocalDate exercicioFim;
    private LocalDate substituidaDesde;
    private UUID ataArquivoId;
    private boolean semAta;
    private LocalDate dataAprovacao;
    private boolean cienteDivergencia;
    private String justificativaDivergencia;
    private String confirmadaPor;
    private Instant confirmadaEm;
    private LocalDate prorrogadaAte;
    private String prorrogacaoJustificativa;
    private String prorrogadaPor;
    private Instant prorrogadaEm;

    protected PrevisaoOrcamentaria() {
    }

    public PrevisaoOrcamentaria(UUID condominioId, UUID arquivoId, String sha256) {
        this.id = UUID.randomUUID();
        this.condominioId = condominioId;
        this.arquivoId = arquivoId;
        this.sha256 = sha256;
    }

    /** Nova leitura do mesmo arquivo (só antes da confirmação). */
    public void registrarLeitura(String interpretador, String titulo, String exercicioImpresso,
            String colunaOrcadoAnterior, String colunaOrcado, EstadoPrevisao estado, BigDecimal totalImpresso,
            BigDecimal previstoMesImpresso, BigDecimal previstoMes, BigDecimal tolerancia, Instant quando) {
        if (this.estado != null && this.estado.travada()) {
            throw new IllegalStateException("PO já confirmada: a leitura não pode ser trocada");
        }
        if (estado.travada()) {
            throw new IllegalArgumentException("A leitura só gera os estados LIDA e LIDA_COM_DIVERGENCIA");
        }
        this.interpretador = interpretador;
        this.titulo = titulo;
        this.exercicioImpresso = exercicioImpresso;
        this.colunaOrcadoAnterior = colunaOrcadoAnterior;
        this.colunaOrcado = colunaOrcado;
        this.estado = estado;
        this.totalImpresso = totalImpresso;
        this.previstoMesImpresso = previstoMesImpresso;
        this.previstoMes = previstoMes;
        this.toleranciaArredondamento = tolerancia;
        this.lidaEm = quando;
    }

    /** RF-03.1.3: confirmação pelo Admin. Validações ficam no serviço de confirmação. */
    public void confirmar(int versao, YearMonth inicio, YearMonth fim, UUID ataArquivoId, boolean semAta,
            LocalDate dataAprovacao, boolean cienteDivergencia, String justificativa, String usuario, Instant quando) {
        if (estado.travada()) {
            throw new IllegalStateException("A PO já foi confirmada");
        }
        this.versao = versao;
        this.exercicioInicio = inicio.atDay(1);
        this.exercicioFim = fim.atDay(1);
        this.ataArquivoId = ataArquivoId;
        this.semAta = semAta;
        this.dataAprovacao = dataAprovacao;
        this.cienteDivergencia = cienteDivergencia;
        this.justificativaDivergencia = justificativa;
        this.confirmadaPor = usuario;
        this.confirmadaEm = quando;
        this.estado = EstadoPrevisao.CONFIRMADA;
    }

    /** Reaprovação: a partir de {@code mes}, vale a nova versão; os meses anteriores continuam com esta. */
    public void substituirAPartirDe(YearMonth mes) {
        if (!estado.travada()) {
            throw new IllegalStateException("Só PO confirmada pode ser substituída");
        }
        LocalDate dia = mes.atDay(1);
        if (substituidaDesde == null || dia.isBefore(substituidaDesde)) {
            substituidaDesde = dia;
        }
        estado = EstadoPrevisao.SUBSTITUIDA;
    }

    /**
     * RF-11.3: a PO vale também nos meses depois do exercício até {@code ate}, com justificativa. As validações
     * (Admin, nenhum mês com PO confirmada) ficam no serviço de prorrogação.
     */
    public void prorrogar(YearMonth ate, String justificativa, String usuario, Instant quando) {
        if (estado != EstadoPrevisao.CONFIRMADA) {
            throw new IllegalStateException("Só PO confirmada (e não substituída) pode ser prorrogada");
        }
        if (!ate.isAfter(getExercicioFim())) {
            throw new IllegalArgumentException("A prorrogação termina depois do fim do exercício");
        }
        this.prorrogadaAte = ate.atDay(1);
        this.prorrogacaoJustificativa = justificativa;
        this.prorrogadaPor = usuario;
        this.prorrogadaEm = quando;
    }

    public void desfazerProrrogacao() {
        this.prorrogadaAte = null;
        this.prorrogacaoJustificativa = null;
        this.prorrogadaPor = null;
        this.prorrogadaEm = null;
    }

    /**
     * PO nova confirmada sobre meses prorrogados (ADR 0005, Decisão 4): a prorrogação passa a terminar em {@code ate};
     * se {@code ate} não passa do fim do exercício, a prorrogação acaba.
     */
    public void encurtarProrrogacao(YearMonth ate) {
        if (prorrogadaAte == null) {
            return;
        }
        if (!ate.isAfter(getExercicioFim())) {
            desfazerProrrogacao();
        } else if (ate.isBefore(getProrrogadaAte())) {
            this.prorrogadaAte = ate.atDay(1);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominioId() {
        return condominioId;
    }

    public UUID getArquivoId() {
        return arquivoId;
    }

    public String getSha256() {
        return sha256;
    }

    public String getInterpretador() {
        return interpretador;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getExercicioImpresso() {
        return exercicioImpresso;
    }

    public String getColunaOrcadoAnterior() {
        return colunaOrcadoAnterior;
    }

    public String getColunaOrcado() {
        return colunaOrcado;
    }

    public EstadoPrevisao getEstado() {
        return estado;
    }

    public BigDecimal getTotalImpresso() {
        return totalImpresso;
    }

    public BigDecimal getPrevistoMesImpresso() {
        return previstoMesImpresso;
    }

    public BigDecimal getPrevistoMes() {
        return previstoMes;
    }

    public BigDecimal getToleranciaArredondamento() {
        return toleranciaArredondamento;
    }

    public Instant getLidaEm() {
        return lidaEm;
    }

    public Integer getVersao() {
        return versao;
    }

    public YearMonth getExercicioInicio() {
        return exercicioInicio == null ? null : YearMonth.from(exercicioInicio);
    }

    public YearMonth getExercicioFim() {
        return exercicioFim == null ? null : YearMonth.from(exercicioFim);
    }

    public YearMonth getSubstituidaDesde() {
        return substituidaDesde == null ? null : YearMonth.from(substituidaDesde);
    }

    public UUID getAtaArquivoId() {
        return ataArquivoId;
    }

    public boolean isSemAta() {
        return semAta;
    }

    public LocalDate getDataAprovacao() {
        return dataAprovacao;
    }

    public boolean isCienteDivergencia() {
        return cienteDivergencia;
    }

    public String getJustificativaDivergencia() {
        return justificativaDivergencia;
    }

    public String getConfirmadaPor() {
        return confirmadaPor;
    }

    public Instant getConfirmadaEm() {
        return confirmadaEm;
    }

    /** Último mês prorrogado, ou nulo sem prorrogação. */
    public YearMonth getProrrogadaAte() {
        return prorrogadaAte == null ? null : YearMonth.from(prorrogadaAte);
    }

    public String getProrrogacaoJustificativa() {
        return prorrogacaoJustificativa;
    }

    public String getProrrogadaPor() {
        return prorrogadaPor;
    }

    public Instant getProrrogadaEm() {
        return prorrogadaEm;
    }
}
