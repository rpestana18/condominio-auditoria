package br.com.condominioauditoria.backend.arquivo;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Registro de um arquivo enviado. O conteúdo fica na pasta de dados; aqui só o caminho, o hash e o status. */
@Entity
public class Arquivo {

    @Id
    private UUID id;
    private UUID condominioId;
    @Enumerated(EnumType.STRING)
    private Categoria categoria;
    private String nomeOriginal;
    private String caminho;
    private String sha256;
    private long tamanhoBytes;
    private String tipoConteudo;
    @Enumerated(EnumType.STRING)
    private StatusArquivo status;
    private String mensagem;
    private String interpretador;
    private LocalDate periodoInicio;
    private LocalDate periodoFim;
    private Integer totalLancamentos;
    private String enviadoPor;
    private Instant enviadoEm;
    private Instant processadoEm;
    /** Identifica a leitura em andamento. Resultado que chega com outro id (velho ou repetido) é descartado. */
    private UUID processamentoId;
    /** Quando foi colocado na fila pela última vez; a varredura reenvia o que ficou parado. */
    private Instant enfileiradoEm;
    private int tentativas;

    protected Arquivo() {
    }

    public Arquivo(UUID condominioId, Categoria categoria, String nomeOriginal, String caminho, String sha256,
            long tamanhoBytes, String tipoConteudo, String enviadoPor) {
        this.id = UUID.randomUUID();
        this.condominioId = condominioId;
        this.categoria = categoria;
        this.nomeOriginal = nomeOriginal;
        this.caminho = caminho;
        this.sha256 = sha256;
        this.tamanhoBytes = tamanhoBytes;
        this.tipoConteudo = tipoConteudo;
        this.enviadoPor = enviadoPor;
        this.enviadoEm = Instant.now();
        this.status = StatusArquivo.PENDENTE;
        this.processamentoId = UUID.randomUUID();
        this.enfileiradoEm = this.enviadoEm;
        this.tentativas = 1;
    }

    /** Nova leitura do zero (reprocessar): resultados de leituras anteriores passam a ser ignorados. */
    public void novoProcessamento() {
        status = StatusArquivo.PENDENTE;
        mensagem = null;
        processamentoId = UUID.randomUUID();
        enfileiradoEm = Instant.now();
        tentativas = 1;
    }

    /** Reenvio da mesma leitura (mensagem perdida ou serviço reiniciado). */
    public void reenviar() {
        enfileiradoEm = Instant.now();
        tentativas++;
    }

    public boolean ehDoProcessamento(UUID id) {
        return processamentoId != null && processamentoId.equals(id);
    }

    public void iniciarProcessamento() {
        status = StatusArquivo.PROCESSANDO;
        mensagem = null;
    }

    public void concluir(StatusArquivo resultado, String mensagem, String interpretador, LocalDate inicio, LocalDate fim,
            Integer totalLancamentos) {
        this.status = resultado;
        this.mensagem = mensagem;
        this.interpretador = interpretador;
        this.periodoInicio = inicio;
        this.periodoFim = fim;
        this.totalLancamentos = totalLancamentos;
        this.processadoEm = Instant.now();
    }

    public void falhar(String motivo) {
        status = StatusArquivo.FALHOU;
        mensagem = motivo;
        processadoEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominioId() {
        return condominioId;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public String getNomeOriginal() {
        return nomeOriginal;
    }

    public String getCaminho() {
        return caminho;
    }

    public String getSha256() {
        return sha256;
    }

    public long getTamanhoBytes() {
        return tamanhoBytes;
    }

    public String getTipoConteudo() {
        return tipoConteudo;
    }

    public StatusArquivo getStatus() {
        return status;
    }

    public String getMensagem() {
        return mensagem;
    }

    public String getInterpretador() {
        return interpretador;
    }

    public LocalDate getPeriodoInicio() {
        return periodoInicio;
    }

    public LocalDate getPeriodoFim() {
        return periodoFim;
    }

    public Integer getTotalLancamentos() {
        return totalLancamentos;
    }

    public String getEnviadoPor() {
        return enviadoPor;
    }

    public Instant getEnviadoEm() {
        return enviadoEm;
    }

    public Instant getProcessadoEm() {
        return processadoEm;
    }

    public UUID getProcessamentoId() {
        return processamentoId;
    }

    public Instant getEnfileiradoEm() {
        return enfileiradoEm;
    }

    public int getTentativas() {
        return tentativas;
    }
}
