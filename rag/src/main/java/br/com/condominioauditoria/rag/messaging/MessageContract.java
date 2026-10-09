package br.com.condominioauditoria.rag.messaging;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * Converts the queue messages to and from JSON and validates them against the contracts, on output and on input:
 * FileReceivedMessage in contracts/mensagens/v1 and ProcessingResultMessage in contracts/mensagens/v2 (ADR 0004).
 * Money goes as text ("1234.56"), never as a floating-point number.
 */
@Component
public class MessageContract {

    private final Schema fileReceived;
    private final Schema processingResult;
    private final Schema indexFile;
    private final Schema indexingResult;
    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new SimpleModule().addSerializer(BigDecimal.class, ToStringSerializer.instance))
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public MessageContract() {
        SchemaRegistry record = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        this.fileReceived = record.getSchema(SchemaLocation.of("classpath:mensagens/v1/arquivo-recebido.schema.json"));
        this.processingResult = record.getSchema(SchemaLocation.of("classpath:mensagens/v2/resultado-processamento.schema.json"));
        this.indexFile = record.getSchema(SchemaLocation.of("classpath:mensagens/v1/indexar-arquivo.schema.json"));
        this.indexingResult = record.getSchema(
                SchemaLocation.of("classpath:mensagens/v1/resultado-indexacao.schema.json"));
    }

    public FileReceivedMessage readFileReceived(byte[] body) {
        String json = new String(body, StandardCharsets.UTF_8);
        validate(fileReceived, json, "ArquivoRecebido v1");
        return mapper.readValue(json, FileReceivedMessage.class);
    }

    public byte[] write(ProcessingResultMessage message) {
        String json = mapper.writeValueAsString(message);
        validate(processingResult, json, "ResultadoProcessamento v2");
        return json.getBytes(StandardCharsets.UTF_8);
    }

    public IndexFileMessage readIndexFile(byte[] body) {
        String json = new String(body, StandardCharsets.UTF_8);
        validate(indexFile, json, "IndexarArquivo");
        return mapper.readValue(json, IndexFileMessage.class);
    }

    public byte[] write(IndexingResultMessage message) {
        String json = mapper.writeValueAsString(message);
        validate(indexingResult, json, "ResultadoIndexacao");
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static void validate(Schema schema, String json, String name) {
        List<Error> errors = schema.validate(json, InputFormat.JSON);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(name + " fora do contrato: "
                    + String.join("; ", errors.stream().limit(5).map(Error::toString).toList()));
        }
    }
}
