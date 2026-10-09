package br.com.condominioauditoria.api.ia;

import br.com.condominioauditoria.api.model.enums.AiMode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Trilha da configuração de IA (RF-09.6, RF-07.4): uma linha por função alterada, só de inserção (o banco recusa
 * update e delete). Nunca guarda a chave: só se ela foi trocada e os 4 últimos caracteres da nova
 * (chaveTrocada com chaveFinal nulo = chave removida).
 */
@Entity
@Immutable
public class EventoConfiguracaoIa {

    @Id
    private UUID id;
    private UUID condominioId;
    private String modulo;
    @Enumerated(EnumType.STRING)
    private FuncaoIa funcao;
    private String usuario;
    private Instant quando;
    @Enumerated(EnumType.STRING)
    private AiMode modoAnterior;
    @Enumerated(EnumType.STRING)
    private AiMode modoNovo;
    private String provedorAnterior;
    private String provedorNovo;
    private String modeloAnterior;
    private String modeloNovo;
    private boolean chaveTrocada;
    private String chaveFinal;

    protected EventoConfiguracaoIa() {
    }

    EventoConfiguracaoIa(UUID condominioId, String modulo, FuncaoIa funcao, String usuario, Instant quando,
            AiMode modoAnterior, AiMode modoNovo, String provedorAnterior, String provedorNovo, String modeloAnterior,
            String modeloNovo, boolean chaveTrocada, String chaveFinal) {
        this.id = UUID.randomUUID();
        this.condominioId = condominioId;
        this.modulo = modulo;
        this.funcao = funcao;
        this.usuario = usuario;
        this.quando = quando;
        this.modoAnterior = modoAnterior;
        this.modoNovo = modoNovo;
        this.provedorAnterior = provedorAnterior;
        this.provedorNovo = provedorNovo;
        this.modeloAnterior = modeloAnterior;
        this.modeloNovo = modeloNovo;
        this.chaveTrocada = chaveTrocada;
        this.chaveFinal = chaveFinal;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominioId() {
        return condominioId;
    }

    public String getModulo() {
        return modulo;
    }

    public FuncaoIa getFuncao() {
        return funcao;
    }

    public String getUsuario() {
        return usuario;
    }

    public Instant getQuando() {
        return quando;
    }

    public AiMode getModoAnterior() {
        return modoAnterior;
    }

    public AiMode getModoNovo() {
        return modoNovo;
    }

    public String getProvedorAnterior() {
        return provedorAnterior;
    }

    public String getProvedorNovo() {
        return provedorNovo;
    }

    public String getModeloAnterior() {
        return modeloAnterior;
    }

    public String getModeloNovo() {
        return modeloNovo;
    }

    public boolean isChaveTrocada() {
        return chaveTrocada;
    }

    public String getChaveFinal() {
        return chaveFinal;
    }
}
