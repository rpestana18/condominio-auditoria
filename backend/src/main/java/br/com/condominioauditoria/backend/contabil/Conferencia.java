package br.com.condominioauditoria.backend.contabil;

import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.ConferenciaLida;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/** Resultado gravado de uma conferência aritmética do arquivo. */
@Entity
public class Conferencia {

    @Id
    private UUID id;
    private UUID arquivoId;
    private int ordem;
    private String codigo;
    private String descricao;
    private boolean ok;
    private String detalhe;

    protected Conferencia() {
    }

    public Conferencia(UUID arquivoId, int ordem, ConferenciaLida v) {
        this.id = UUID.randomUUID();
        this.arquivoId = arquivoId;
        this.ordem = ordem;
        this.codigo = v.codigo();
        this.descricao = v.descricao();
        this.ok = v.ok();
        this.detalhe = v.detalhe();
    }

    public UUID getArquivoId() {
        return arquivoId;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescricao() {
        return descricao;
    }

    public boolean isOk() {
        return ok;
    }

    public String getDetalhe() {
        return detalhe;
    }
}
