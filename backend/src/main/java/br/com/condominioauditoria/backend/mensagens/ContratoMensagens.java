package br.com.condominioauditoria.backend.mensagens;

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
 * Converte as mensagens da fila de e para JSON e valida contra os contratos de contracts/mensagens/v1, na saída e na
 * entrada. Mensagem fora do contrato não entra no banco.
 */
@Component
class ContratoMensagens {

    private final Schema arquivoRecebido;
    private final Schema resultado;
    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    ContratoMensagens() {
        SchemaRegistry registro = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        this.arquivoRecebido = registro.getSchema(SchemaLocation.of("classpath:mensagens/v1/arquivo-recebido.schema.json"));
        this.resultado = registro.getSchema(SchemaLocation.of("classpath:mensagens/v1/resultado-processamento.schema.json"));
    }

    byte[] escrever(ArquivoRecebido mensagem) {
        String json = mapper.writeValueAsString(mensagem);
        validar(arquivoRecebido, json, "ArquivoRecebido");
        return json.getBytes(StandardCharsets.UTF_8);
    }

    ResultadoProcessamento lerResultado(byte[] corpo) {
        String json = new String(corpo, StandardCharsets.UTF_8);
        validar(resultado, json, "ResultadoProcessamento");
        return mapper.readValue(json, ResultadoProcessamento.class);
    }

    private static void validar(Schema esquema, String json, String nome) {
        List<Error> erros = esquema.validate(json, InputFormat.JSON);
        if (!erros.isEmpty()) {
            throw new IllegalArgumentException(nome + " fora do contrato v1: "
                    + String.join("; ", erros.stream().limit(5).map(Error::toString).toList()));
        }
    }
}
