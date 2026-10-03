package br.com.condominioauditoria.rag.processamento;

import br.com.condominioauditoria.rag.config.PropriedadesRag;
import br.com.condominioauditoria.rag.leitura.contrato.ContratoLeitor;
import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido;
import java.time.Duration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/** Chama o leitor Python (arquivo → JSON) e valida a resposta contra o contrato v1. */
@Component
class LeitorDocumentosHttp {

    private final RestClient cliente;
    private final ContratoLeitor contrato;

    LeitorDocumentosHttp(RestClient.Builder builder, PropriedadesRag propriedades, ContratoLeitor contrato) {
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(10));
        fabrica.setReadTimeout(Duration.ofSeconds(propriedades.leitor().timeoutSegundos()));
        this.cliente = builder.baseUrl(propriedades.leitor().url()).requestFactory(fabrica).build();
        this.contrato = contrato;
    }

    DocumentoLido ler(String nome, byte[] conteudo) {
        var partes = new LinkedMultiValueMap<String, Object>();
        partes.add("arquivo", new ByteArrayResource(conteudo) {
            @Override
            public String getFilename() {
                return nome;
            }
        });
        String json = cliente.post().uri("/v1/ler")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(partes)
                .retrieve()
                .body(String.class);
        return contrato.converter(json);
    }
}
