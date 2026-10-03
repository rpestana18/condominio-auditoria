package br.com.condominioauditoria.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Parâmetros do sistema (bloco "condominio" do application.yml). */
@ConfigurationProperties(prefix = "condominio")
public record PropriedadesCondominio(Armazenamento armazenamento, Leitor leitor, Processamento processamento) {

    /** tipo = local no MVP; na nuvem entra outro tipo (ex.: s3) sem mudar o código de quem usa. */
    public record Armazenamento(String tipo, String pasta) {
    }

    public record Leitor(String url, int timeoutSegundos) {
    }

    public record Processamento(int threads) {
    }
}
