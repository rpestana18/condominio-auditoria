package br.com.condominioauditoria.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/** Parâmetros do backend (bloco "condominio" do application.yml). */
@ConfigurationProperties(prefix = "condominio")
public record PropriedadesCondominio(Armazenamento armazenamento, Processamento processamento, Grpc grpc,
        Rag rag) {

    /** tipo = local no MVP; na nuvem entra outro tipo (ex.: s3) sem mudar o código de quem usa. */
    public record Armazenamento(String tipo, String pasta) {
    }

    /** Arquivo parado na fila há mais que reenviarAposMinutos é reenviado ao rag, até maxTentativas. */
    public record Processamento(int reenviarAposMinutos, int maxTentativas) {
    }

    /** Porta do servidor gRPC de consulta (usado pelo serviço mcp). */
    public record Grpc(int porta) {
    }

    /**
     * Cliente gRPC do assistente no rag (contracts/grpc/assistente/v1): endereço host:porta e prazos.
     * prazoSegundos vale para Buscar e ListarProvedores; prazoPerguntaSegundos para Perguntar (o modelo pode levar
     * mais tempo, com ferramentas e nova tentativa). O catálogo de provedores (ListarProvedores) fica em memória por
     * catalogoCacheSegundos.
     */
    public record Rag(String grpc, int prazoSegundos, int prazoPerguntaSegundos, int catalogoCacheSegundos) {

        public static final int PRAZO_PERGUNTA_PADRAO = 120;
        public static final int CATALOGO_CACHE_PADRAO = 300;

        @ConstructorBinding
        public Rag {
            prazoPerguntaSegundos = prazoPerguntaSegundos > 0 ? prazoPerguntaSegundos : PRAZO_PERGUNTA_PADRAO;
            catalogoCacheSegundos = catalogoCacheSegundos >= 0 ? catalogoCacheSegundos : CATALOGO_CACHE_PADRAO;
        }

        public Rag(String grpc, int prazoSegundos) {
            this(grpc, prazoSegundos, PRAZO_PERGUNTA_PADRAO, CATALOGO_CACHE_PADRAO);
        }
    }
}
