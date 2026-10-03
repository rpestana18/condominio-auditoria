package br.com.condominioauditoria.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Serviço rag: recebe da fila os arquivos guardados pelo backend, lê (leitor Python), interpreta, confere e devolve
 * os dados extraídos pela fila. Não tem banco de negócio: quem grava os dados contábeis é o backend.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
