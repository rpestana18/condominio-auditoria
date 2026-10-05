package br.com.condominioauditoria.backend.modulo;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Uma operação de um módulo (RF-09.7). Só inclusão (o banco recusa update e delete). Nunca guarda texto de documento,
 * texto da busca ou chave de API; só contagens, modo, provedor e modelo.
 */
@Entity
@Immutable
public class UsoModulo {

    @Id
    private UUID id;
    private UUID condominioId;
    private String modulo;
    private FuncaoUso funcao;
    /** Nulo em processamento em segundo plano (ex.: indexação). */
    private String usuario;
    private Instant quando;
    @Enumerated(EnumType.STRING)
    private ModoIa modo;
    private String provedor;
    private String modelo;
    private Long tokensEntrada;
    private Long tokensSaida;
    private Integer arquivos;
    private Integer paginas;
    private String versaoPrompt;

    protected UsoModulo() {
    }

    UsoModulo(UUID condominioId, String modulo, FuncaoUso funcao, String usuario, Instant quando, ModoIa modo,
            String provedor, String modelo, Long tokensEntrada, Long tokensSaida, Integer arquivos, Integer paginas,
            String versaoPrompt) {
        this.id = UUID.randomUUID();
        this.condominioId = condominioId;
        this.modulo = modulo;
        this.funcao = funcao;
        this.usuario = usuario;
        this.quando = quando;
        this.modo = modo;
        this.provedor = provedor;
        this.modelo = modelo;
        this.tokensEntrada = tokensEntrada;
        this.tokensSaida = tokensSaida;
        this.arquivos = arquivos;
        this.paginas = paginas;
        this.versaoPrompt = versaoPrompt;
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

    public FuncaoUso getFuncao() {
        return funcao;
    }

    public String getUsuario() {
        return usuario;
    }

    public Instant getQuando() {
        return quando;
    }

    public ModoIa getModo() {
        return modo;
    }

    public String getProvedor() {
        return provedor;
    }

    public String getModelo() {
        return modelo;
    }

    public Long getTokensEntrada() {
        return tokensEntrada;
    }

    public Long getTokensSaida() {
        return tokensSaida;
    }

    public Integer getArquivos() {
        return arquivos;
    }

    public Integer getPaginas() {
        return paginas;
    }

    public String getVersaoPrompt() {
        return versaoPrompt;
    }
}
