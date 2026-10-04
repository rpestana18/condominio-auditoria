package br.com.condominioauditoria.backend.orcamento;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Linha da PO como impressa, com arquivo, página e hash de origem (RF-03.1.1). "%" e Observações são texto lido e não
 * entram em cálculo. O id é o destino do de-para; o código efetivo só difere do impresso quando o código se repete.
 */
@Entity
public class LinhaPo {

    @Id
    private UUID id;
    private UUID previsaoId;
    private UUID condominioId;
    private UUID arquivoId;
    private String sha256;
    private int ordem;
    private int pagina;
    @Enumerated(EnumType.STRING)
    private TipoLinhaPo tipo;
    private String codigoImpresso;
    private String codigoEfetivo;
    private String conta;
    private String contaTexto;
    @Enumerated(EnumType.STRING)
    private MarcaPo marca;
    private String descricao;
    private BigDecimal orcadoAnterior;
    private BigDecimal orcado;
    private String percentualTexto;
    private String observacoes;

    protected LinhaPo() {
    }

    public LinhaPo(PrevisaoOrcamentaria previsao, int ordem, int pagina, TipoLinhaPo tipo, String codigoImpresso,
            String conta, String contaTexto, MarcaPo marca, String descricao, BigDecimal orcadoAnterior,
            BigDecimal orcado, String percentualTexto, String observacoes) {
        this.id = UUID.randomUUID();
        this.previsaoId = previsao.getId();
        this.condominioId = previsao.getCondominioId();
        this.arquivoId = previsao.getArquivoId();
        this.sha256 = previsao.getSha256();
        this.ordem = ordem;
        this.pagina = pagina;
        this.tipo = tipo;
        this.codigoImpresso = codigoImpresso;
        this.codigoEfetivo = codigoImpresso;
        this.conta = conta;
        this.contaTexto = contaTexto;
        this.marca = marca;
        this.descricao = descricao;
        this.orcadoAnterior = orcadoAnterior;
        this.orcado = orcado;
        this.percentualTexto = percentualTexto;
        this.observacoes = observacoes;
    }

    /** Código distinto para a linha cujo código impresso se repete (RF-03.1.2). O valor lido não muda. */
    public void definirCodigoEfetivo(String codigo) {
        this.codigoEfetivo = codigo;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPrevisaoId() {
        return previsaoId;
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

    public int getOrdem() {
        return ordem;
    }

    public int getPagina() {
        return pagina;
    }

    public TipoLinhaPo getTipo() {
        return tipo;
    }

    public String getCodigoImpresso() {
        return codigoImpresso;
    }

    public String getCodigoEfetivo() {
        return codigoEfetivo;
    }

    public String getConta() {
        return conta;
    }

    public String getContaTexto() {
        return contaTexto;
    }

    public MarcaPo getMarca() {
        return marca;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getOrcadoAnterior() {
        return orcadoAnterior;
    }

    public BigDecimal getOrcado() {
        return orcado;
    }

    public String getPercentualTexto() {
        return percentualTexto;
    }

    public String getObservacoes() {
        return observacoes;
    }
}
