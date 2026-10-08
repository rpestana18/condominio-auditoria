package br.com.condominioauditoria.api.ia;

import br.com.condominioauditoria.api.modulo.ModoIa;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/**
 * Uma linha da configuração de IA do condomínio (tabela configuracao_ia, V13): módulo nulo = modo geral; ASSISTENTE +
 * RESPOSTAS = chat (modo nulo = herda o geral); ASSISTENTE + EMBEDDINGS = busca por significado.
 *
 * A chave de API só existe cifrada com a chave pública do rag (o backend não consegue lê-la) e nunca sai em
 * toString, log ou resposta da API; chaveFinal guarda os 4 últimos caracteres para a tela.
 */
@Entity
public class ConfiguracaoIa {

    @Id
    private UUID id;
    private UUID condominioId;
    private String modulo;
    @Enumerated(EnumType.STRING)
    private FuncaoIa funcao;
    @Enumerated(EnumType.STRING)
    private ModoIa modo;
    private String provedor;
    private String modelo;
    private byte[] chaveCifrada;
    private String chaveFinal;
    private String atualizadoPor;
    private Instant atualizadoEm;

    protected ConfiguracaoIa() {
    }

    ConfiguracaoIa(UUID condominioId, String modulo, FuncaoIa funcao) {
        this.id = UUID.randomUUID();
        this.condominioId = condominioId;
        this.modulo = modulo;
        this.funcao = funcao;
    }

    void alterar(ModoIa modo, String provedor, String modelo, String usuario, Instant quando) {
        this.modo = modo;
        this.provedor = provedor;
        this.modelo = modelo;
        this.atualizadoPor = usuario;
        this.atualizadoEm = quando;
    }

    void trocarChave(byte[] chaveCifrada, String chaveFinal) {
        this.chaveCifrada = chaveCifrada == null ? null : chaveCifrada.clone();
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

    public ModoIa getModo() {
        return modo;
    }

    public String getProvedor() {
        return provedor;
    }

    public String getModelo() {
        return modelo;
    }

    public byte[] getChaveCifrada() {
        return chaveCifrada == null ? null : chaveCifrada.clone();
    }

    public boolean temChave() {
        return chaveCifrada != null && chaveCifrada.length > 0;
    }

    public String getChaveFinal() {
        return chaveFinal;
    }

    public String getAtualizadoPor() {
        return atualizadoPor;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    @Override
    public String toString() {
        return "ConfiguracaoIa[" + condominioId + ", " + modulo + ", " + funcao + ", " + modo + ", " + provedor + "/"
                + modelo + ", chave " + (temChave() ? "cadastrada" : "ausente") + "]";
    }
}
