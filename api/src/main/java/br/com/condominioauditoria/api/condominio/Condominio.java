package br.com.condominioauditoria.api.condominio;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

@Entity
public class Condominio {

    @Id
    private UUID id;
    private String nome;
    private String cnpj;
    private Instant criadoEm;
    private UUID fundoOrdinarioId;
    private String fundoOrdinarioConfirmadoPor;
    private Instant fundoOrdinarioConfirmadoEm;

    protected Condominio() {
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getCnpj() {
        return cnpj;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public UUID getFundoOrdinarioId() {
        return fundoOrdinarioId;
    }

    public String getFundoOrdinarioConfirmadoPor() {
        return fundoOrdinarioConfirmadoPor;
    }

    public Instant getFundoOrdinarioConfirmadoEm() {
        return fundoOrdinarioConfirmadoEm;
    }

    /** O Gestor ou o Admin confirma qual fundo é o ordinário (RF-05.1b). */
    public void confirmarFundoOrdinario(UUID fundoId, String usuario, Instant quando) {
        this.fundoOrdinarioId = fundoId;
        this.fundoOrdinarioConfirmadoPor = usuario;
        this.fundoOrdinarioConfirmadoEm = quando;
    }
}
