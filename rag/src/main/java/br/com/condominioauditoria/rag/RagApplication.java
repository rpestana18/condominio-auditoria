package br.com.condominioauditoria.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Serviço rag: recebe da fila os arquivos guardados pelo backend, lê (leitor Python), interpreta, confere e devolve
 * os dados extraídos pela fila. Não grava dados contábeis (isso é do backend); o banco dele (schema rag) guarda só o
 * índice dos documentos para a busca do assistente (ADR 0003), servida por gRPC ao backend.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
