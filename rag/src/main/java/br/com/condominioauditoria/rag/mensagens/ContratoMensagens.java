package br.com.condominioauditoria.rag.mensagens;

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
 * Converte as mensagens da fila de e para JSON e valida contra os contratos de contracts/mensagens/v1, na saída e na
 * entrada. Dinheiro vai como texto ("1234.56"), nunca como número de ponto flutuante.
 */
@Component
public class ContratoMensagens {

    private final Schema arquivoRecebido;
    private final Schema resultado;
    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new SimpleModule().addSerializer(BigDecimal.class, ToStringSerializer.instance))
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public ContratoMensagens() {
        SchemaRegistry registro = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        this.arquivoRecebido = registro.getSchema(SchemaLocation.of("classpath:mensagens/v1/arquivo-recebido.schema.json"));
        this.resultado = registro.getSchema(SchemaLocation.of("classpath:mensagens/v1/resultado-processamento.schema.json"));
    }

    public ArquivoRecebido lerArquivoRecebido(byte[] corpo) {
        String json = new String(corpo, StandardCharsets.UTF_8);
        validar(arquivoRecebido, json, "ArquivoRecebido");
        return mapper.readValue(json, ArquivoRecebido.class);
    }

    public byte[] escrever(ResultadoProcessamento mensagem) {
        String json = mapper.writeValueAsString(mensagem);
        validar(resultado, json, "ResultadoProcessamento");
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static void validar(Schema esquema, String json, String nome) {
        List<Error> erros = esquema.validate(json, InputFormat.JSON);
        if (!erros.isEmpty()) {
            throw new IllegalArgumentException(nome + " fora do contrato v1: "
                    + String.join("; ", erros.stream().limit(5).map(Error::toString).toList()));
        }
    }
}
