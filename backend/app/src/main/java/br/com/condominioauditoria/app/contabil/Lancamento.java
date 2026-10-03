package br.com.condominioauditoria.app.contabil;

import br.com.condominioauditoria.dominio.fluxo.LancamentoFluxo;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Lançamento extraído, normalizado e enriquecido. Aponta para o arquivo e a página de origem. */
@Entity
public class Lancamento {

    @Id
    private UUID id;
    private UUID condominioId;
    private UUID arquivoId;
    private UUID fundoId;
    private LocalDate data;
    private String contaCodigo;
    private String contaNome;
    private String documento;
    private String historico;
    private BigDecimal credito;
    private BigDecimal debito;
    private BigDecimal saldo;
    private int pagina;
    private int ordem;
    private String notaFiscal;
    private String fornecedor;
    private String meioPagamento;
    private boolean transferenciaEntreFundos;

    protected Lancamento() {
    }

    public Lancamento(UUID condominioId, UUID arquivoId, UUID fundoId, LancamentoFluxo l) {
        this.id = UUID.randomUUID();
        this.condominioId = condominioId;
        this.arquivoId = arquivoId;
        this.fundoId = fundoId;
        this.data = l.data();
        this.contaCodigo = l.contaCodigo();
        this.contaNome = l.contaNome();
        this.documento = l.documento();
        this.historico = l.historico();
        this.credito = l.credito();
        this.debito = l.debito();
        this.saldo = l.saldo();
        this.pagina = l.pagina();
        this.ordem = l.ordem();
        this.notaFiscal = l.enriquecimento().notaFiscal();
        this.fornecedor = l.enriquecimento().fornecedor();
        this.meioPagamento = l.enriquecimento().meioPagamento();
        this.transferenciaEntreFundos = l.enriquecimento().transferenciaEntreFundos();
    }

    public UUID getId() {
        return id;
    }

    public UUID getFundoId() {
        return fundoId;
    }

    public LocalDate getData() {
        return data;
    }

    public String getContaCodigo() {
        return contaCodigo;
    }

    public String getContaNome() {
        return contaNome;
    }

    public String getDocumento() {
        return documento;
    }

    public String getHistorico() {
        return historico;
    }

    public BigDecimal getCredito() {
        return credito;
    }

    public BigDecimal getDebito() {
        return debito;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public int getPagina() {
        return pagina;
    }

    public int getOrdem() {
        return ordem;
    }

    public String getNotaFiscal() {
        return notaFiscal;
    }

    public String getFornecedor() {
        return fornecedor;
    }

    public String getMeioPagamento() {
        return meioPagamento;
    }

    public boolean isTransferenciaEntreFundos() {
        return transferenciaEntreFundos;
    }
}
