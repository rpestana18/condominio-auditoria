package br.com.condominioauditoria.rag.parser;

import br.com.condominioauditoria.rag.model.document.ReadDocument;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.List;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validates the reader's JSON against the versioned JSON Schema and converts it to {@link ReadDocument}. If the
 * reader changes its output without changing the contract, the failure shows up here, with the reason.
 */
public final class ReaderContract {

    private static final String SCHEMA = "classpath:leitor/v1/documento-lido.schema.json";

    private final Schema schema;
    private final JsonMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public ReaderContract() {
        SchemaRegistry record = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        this.schema = record.getSchema(SchemaLocation.of(SCHEMA));
    }

    public ReadDocument convert(String json) {
        List<Error> errors = schema.validate(json, InputFormat.JSON);
        if (!errors.isEmpty()) {
            throw new InvalidContractException(errors.stream().limit(5).map(Error::toString).toList());
        }
        return mapper.readValue(json, ReadDocument.class);
    }

    public static class InvalidContractException extends RuntimeException {
        public InvalidContractException(List<String> errors) {
            super("Saída do leitor fora do contrato v1: " + String.join("; ", errors));
        }
    }
}
