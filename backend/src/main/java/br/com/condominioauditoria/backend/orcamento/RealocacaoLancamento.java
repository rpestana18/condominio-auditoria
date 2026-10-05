package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.contabil.ImpressaoLancamento;
import br.com.condominioauditoria.backend.contabil.Lancamento;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Realocação de um lançamento "a realocar" para uma linha da PO (RF-03.1.7; RF-02B.4). É uma camada do sistema: o
 * lançamento original da administradora não muda. Guarda a impressão do lançamento e a chave dela, porque o id do
 * lançamento muda a cada reprocesso. Desfazer encerra a realocação; nada é apagado.
 */
@Entity
@Table(name = "realocacao")
public class RealocacaoLancamento {

    @Id
    private UUID id;
    private UUID condominioId;
    private UUID previsaoId;
    private String chaveLancamento;
    private UUID arquivoId;
    private String sha256;
    private int pagina;
    private int ordem;
    private LocalDate data;
    private String contaCodigo;
    private String contaNome;
    private String documento;
    private String historico;
    private BigDecimal valor;
    private UUID linhaPoId;
    private String realocadaPor;
    private Instant realocadaEm;
    private String desfeitaPor;
    private Instant desfeitaEm;

    protected RealocacaoLancamento() {
    }

    public RealocacaoLancamento(PrevisaoOrcamentaria po, Lancamento l, String sha256, LinhaPo destino, String usuario,
            Instant em) {
        this.id = UUID.randomUUID();
        this.condominioId = po.getCondominioId();
        this.previsaoId = po.getId();
        this.chaveLancamento = ImpressaoLancamento.chave(l);
        this.arquivoId = l.getArquivoId();
        this.sha256 = sha256;
        this.pagina = l.getPagina();
        this.ordem = l.getOrdem();
        this.data = l.getData();
        this.contaCodigo = l.getContaCodigo();
        this.contaNome = l.getContaNome();
        this.documento = l.getDocumento();
        this.historico = l.getHistorico();
        this.valor = l.getDebito();
        this.linhaPoId = destino.getId();
        this.realocadaPor = usuario;
        this.realocadaEm = em;
    }

    public void desfazer(String usuario, Instant em) {
        if (desfeitaEm != null) {
            throw new IllegalStateException("Realocação já desfeita");
        }
        this.desfeitaPor = usuario;
        this.desfeitaEm = em;
    }

    public boolean ativa() {
        return desfeitaEm == null;
    }

    /** Como o cálculo usa (casamento pela chave). */
    public CalculoPrevistoRealizado.Realocacao paraCalculo() {
        return new CalculoPrevistoRealizado.Realocacao(id, chaveLancamento, arquivoId, data, contaCodigo, valor, pagina,
                linhaPoId, realocadaPor, realocadaEm);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominioId() {
        return condominioId;
    }

    public UUID getPrevisaoId() {
        return previsaoId;
    }

    public String getChaveLancamento() {
        return chaveLancamento;
    }

    public UUID getArquivoId() {
        return arquivoId;
    }

    public String getSha256() {
        return sha256;
    }

    public int getPagina() {
        return pagina;
    }

    public int getOrdem() {
        return ordem;
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

    public BigDecimal getValor() {
        return valor;
    }

    public UUID getLinhaPoId() {
        return linhaPoId;
    }

    public String getRealocadaPor() {
        return realocadaPor;
    }

    public Instant getRealocadaEm() {
        return realocadaEm;
    }

    public String getDesfeitaPor() {
        return desfeitaPor;
    }

    public Instant getDesfeitaEm() {
        return desfeitaEm;
    }
}
