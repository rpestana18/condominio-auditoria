package br.com.condominioauditoria.rag.client;

import br.com.condominioauditoria.logging.LogContext;
import br.com.condominioauditoria.rag.config.properties.RagProperties;
import br.com.condominioauditoria.rag.model.document.ReadDocument;
import br.com.condominioauditoria.rag.parser.ReaderContract;
import java.time.Duration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/** Calls the Python reader (file → JSON) and validates the response against the v2 contract. */
@Component
public class DocumentReaderClient {

    private final RestClient client;
    private final ReaderContract contract;

    public DocumentReaderClient(RestClient.Builder builder, RagProperties properties, ReaderContract contract) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(properties.reader().timeoutSeconds()));
        this.client = builder.baseUrl(properties.reader().url()).requestFactory(factory).build();
        this.contract = contract;
    }

    public ReadDocument read(String name, byte[] content) {
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return name;
            }
        });
        String json = client.post().uri("/v2/read")
                // The reader logs with the same request id (ADR 0008)
                .header(LogContext.HTTP_HEADER, LogContext.currentOrNewRequestId())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(String.class);
        return contract.convert(json);
    }
}
