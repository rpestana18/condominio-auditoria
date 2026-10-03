package br.com.condominioauditoria.ingestao.contrato;

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
 * Valida o JSON do leitor contra o JSON Schema versionado e converte para {@link DocumentoLido}.
 * Se o leitor mudar a saída sem mudar o contrato, a falha aparece aqui, com o motivo.
 */
public final class ContratoLeitor {

    private static final String ESQUEMA = "classpath:ingestion/v1/documento-lido.schema.json";

    private final Schema esquema;
    private final JsonMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public ContratoLeitor() {
        SchemaRegistry registro = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        this.esquema = registro.getSchema(SchemaLocation.of(ESQUEMA));
    }

    public DocumentoLido converter(String json) {
        List<Error> erros = esquema.validate(json, InputFormat.JSON);
        if (!erros.isEmpty()) {
            throw new ContratoInvalidoException(erros.stream().limit(5).map(Error::toString).toList());
        }
        return mapper.readValue(json, DocumentoLido.class);
    }

    public static class ContratoInvalidoException extends RuntimeException {
        public ContratoInvalidoException(List<String> erros) {
            super("Saída do leitor fora do contrato v1: " + String.join("; ", erros));
        }
    }
}
