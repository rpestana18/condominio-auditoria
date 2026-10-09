package br.com.condominioauditoria.api.messaging;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converts the queue messages to and from JSON and validates them against the contracts, outgoing and incoming:
 * ArquivoRecebido in contracts/mensagens/v1 and ResultadoProcessamento in contracts/mensagens/v2 (ADR 0004). A message
 * outside the contract never reaches the database: the listener rejects it and it goes to backend.resultados.erro. A v1
 * result message still in the queue at switch time is rejected this way; the sweep resends the file and the rag answers
 * in v2.
 */
@Component
class MessageContract {

    private final Schema fileReceived;
    private final Schema processingResult;
    private final Schema indexFile;
    private final Schema indexingResult;
    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    MessageContract() {
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        this.fileReceived = registry.getSchema(SchemaLocation.of("classpath:mensagens/v1/arquivo-recebido.schema.json"));
        this.processingResult = registry.getSchema(SchemaLocation.of("classpath:mensagens/v2/resultado-processamento.schema.json"));
        this.indexFile = registry.getSchema(SchemaLocation.of("classpath:mensagens/v1/indexar-arquivo.schema.json"));
        this.indexingResult = registry.getSchema(
                SchemaLocation.of("classpath:mensagens/v1/resultado-indexacao.schema.json"));
    }

    byte[] write(FileReceivedMessage message) {
        String json = mapper.writeValueAsString(message);
        validate(fileReceived, json, "ArquivoRecebido v1");
        return json.getBytes(StandardCharsets.UTF_8);
    }

    ProcessingResultMessage readProcessingResult(byte[] body) {
        String json = new String(body, StandardCharsets.UTF_8);
        validate(processingResult, json, "ResultadoProcessamento v2");
        return mapper.readValue(json, ProcessingResultMessage.class);
    }

    byte[] write(IndexFileMessage message) {
        String json = mapper.writeValueAsString(message);
        validate(indexFile, json, "IndexarArquivo");
        return json.getBytes(StandardCharsets.UTF_8);
    }

    IndexingResultMessage readIndexingResult(byte[] body) {
        String json = new String(body, StandardCharsets.UTF_8);
        validate(indexingResult, json, "ResultadoIndexacao");
        return mapper.readValue(json, IndexingResultMessage.class);
    }

    private static void validate(Schema schema, String json, String name) {
        List<Error> errors = schema.validate(json, InputFormat.JSON);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(name + " fora do contrato: "
                    + String.join("; ", errors.stream().limit(5).map(Error::toString).toList()));
        }
    }
}
