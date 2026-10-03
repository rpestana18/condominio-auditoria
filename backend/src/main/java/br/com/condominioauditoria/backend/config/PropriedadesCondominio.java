package br.com.condominioauditoria.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Parâmetros do backend (bloco "condominio" do application.yml). */
@ConfigurationProperties(prefix = "condominio")
public record PropriedadesCondominio(Armazenamento armazenamento, Processamento processamento, Grpc grpc) {

    /** tipo = local no MVP; na nuvem entra outro tipo (ex.: s3) sem mudar o código de quem usa. */
    public record Armazenamento(String tipo, String pasta) {
    }

    /** Arquivo parado na fila há mais que reenviarAposMinutos é reenviado ao rag, até maxTentativas. */
    public record Processamento(int reenviarAposMinutos, int maxTentativas) {
    }

    /** Porta do servidor gRPC de consulta (usado pelo serviço mcp). */
    public record Grpc(int porta) {
    }
}
