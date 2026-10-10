package br.com.condominioauditoria.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * rag service: receives from the queue the files stored by the api, reads them (Python reader), parses, checks and
 * returns the extracted data through the queue. It does not store accounting data (that belongs to the api); its
 * database (schema rag) holds only the document index for the assistant search (ADR 0003), served over gRPC to the
 * api.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
