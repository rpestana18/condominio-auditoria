package br.com.condominioauditoria.api.auditoria;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/** Onde está o indício: arquivo, hash, página e o trecho (ex.: a linha da PO). */
@Entity
public class AchadoEvidencia {

    @Id
    private UUID id;
    private UUID achadoId;
    private int ordem;
    private UUID arquivoId;
    private String sha256;
    private Integer pagina;
    private String referencia;
    private UUID linhaPoId;

    protected AchadoEvidencia() {
    }

    public AchadoEvidencia(UUID achadoId, int ordem, UUID arquivoId, String sha256, Integer pagina, String referencia,
            UUID linhaPoId) {
        this.id = UUID.randomUUID();
        this.achadoId = achadoId;
        this.ordem = ordem;
        this.arquivoId = arquivoId;
        this.sha256 = sha256;
        this.pagina = pagina;
        this.referencia = referencia;
        this.linhaPoId = linhaPoId;
    }

    public UUID getAchadoId() {
        return achadoId;
    }

    public int getOrdem() {
        return ordem;
    }

    public UUID getArquivoId() {
        return arquivoId;
    }

    public String getSha256() {
        return sha256;
    }

    public Integer getPagina() {
        return pagina;
    }

    public String getReferencia() {
        return referencia;
    }

    public UUID getLinhaPoId() {
        return linhaPoId;
    }
}
