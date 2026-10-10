package br.com.condominioauditoria.rag.config;

import br.com.condominioauditoria.rag.config.properties.RagProperties;
import java.time.Duration;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Ollama client with a short connect timeout: Ollama down becomes a readable error within seconds (indexing = ERROR,
 * hybrid search = keyword search), without holding the queue. Replaces the OllamaApi from Spring AI
 * auto-configuration.
 */
@Configuration
class OllamaConfig {

    @Bean
    OllamaApi ollamaApi(@Value("${spring.ai.ollama.base-url}") String url, RagProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.embeddings().connectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.embeddings().timeoutSeconds()));
        return OllamaApi.builder()
                .baseUrl(url)
                .restClientBuilder(RestClient.builder().requestFactory(factory))
                .build();
    }
}
