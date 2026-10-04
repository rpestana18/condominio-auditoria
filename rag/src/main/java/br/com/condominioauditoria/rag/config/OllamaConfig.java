package br.com.condominioauditoria.rag.config;

import java.time.Duration;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente do Ollama com prazo de conexão curto: Ollama fora do ar vira erro legível em segundos (indexação = ERRO,
 * busca híbrida = busca por palavra), sem prender a fila. Substitui o OllamaApi da auto-configuração do Spring AI.
 */
@Configuration
class OllamaConfig {

    @Bean
    OllamaApi ollamaApi(@Value("${spring.ai.ollama.base-url}") String url, PropriedadesRag propriedades) {
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(propriedades.embeddings().timeoutConexaoSegundos()));
        fabrica.setReadTimeout(Duration.ofSeconds(propriedades.embeddings().timeoutSegundos()));
        return OllamaApi.builder()
                .baseUrl(url)
                .restClientBuilder(RestClient.builder().requestFactory(fabrica))
                .build();
    }
}
