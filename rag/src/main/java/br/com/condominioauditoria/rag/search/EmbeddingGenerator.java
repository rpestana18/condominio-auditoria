package br.com.condominioauditoria.rag.search;

import br.com.condominioauditoria.rag.config.properties.RagProperties;
import br.com.condominioauditoria.rag.exception.EmbeddingsUnavailableException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/**
 * Generates the vectors of the chunks and of the question with the local Ollama (ADR 0003, Decision 2: bge-m3, 1024
 * dimensions). The same model is used to index and to search; changing the model requires reindexing.
 *
 * No text leaves the machine: Ollama runs on the internal network. A failure becomes an
 * {@link EmbeddingsUnavailableException} with a readable reason; the caller decides (indexing = ERROR; hybrid search =
 * falls back to keyword search).
 */
@Component
public class EmbeddingGenerator {

    /** Fixed dimension of the index's vector column. */
    public static final int DIMENSIONS = 1024;

    private final EmbeddingModel model;
    private final String modelName;
    private final String url;
    private final int batchSize;

    EmbeddingGenerator(EmbeddingModel model, @Value("${spring.ai.ollama.embedding.model}") String modelName,
            @Value("${spring.ai.ollama.base-url}") String url, RagProperties properties) {
        this.model = model;
        this.modelName = modelName;
        this.url = url;
        this.batchSize = Math.max(1, properties.embeddings().batchSize());
    }

    /** Name of the configured model (e.g. bge-m3); goes into each stored vector and into the indexing result. */
    public String model() {
        return modelName;
    }

    /**
     * Empty or equal to the configured one = ok. Another model does not exist in this rag yet (catalog is delivery 2).
     */
    public boolean accepts(String requestedModel) {
        return requestedModel == null || requestedModel.isBlank() || requestedModel.equals(modelName);
    }

    /** One vector per text, in the same order. */
    public List<float[]> generate(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> part = texts.subList(i, Math.min(texts.size(), i + batchSize));
            List<float[]> generated = call(part);
            if (generated.size() != part.size()) {
                throw new EmbeddingsUnavailableException("O Ollama devolveu " + generated.size() + " vetores para "
                        + part.size() + " trechos", null);
            }
            vectors.addAll(generated);
        }
        return vectors;
    }

    public float[] generate(String text) {
        return generate(List.of(text)).getFirst();
    }

    private List<float[]> call(List<String> texts) {
        List<float[]> vectors;
        try {
            vectors = model.embed(texts);
        } catch (ResourceAccessException error) {
            throw new EmbeddingsUnavailableException("O serviço de embeddings (Ollama) não respondeu em " + url
                    + ". Ele está rodando? (" + error.getMessage() + ")", error);
        } catch (RuntimeException error) {
            String message = String.valueOf(error.getMessage());
            if (message.contains("not found")) { // o Ollama responde 404 "model ... not found, try pulling it first"
                throw new EmbeddingsUnavailableException("O modelo " + modelName
                        + " não está baixado no Ollama em " + url + " (" + message + ")", error);
            }
            throw new EmbeddingsUnavailableException("Falha ao gerar embeddings no Ollama: " + message, error);
        }
        for (float[] v : vectors) {
            if (v.length != DIMENSIONS) {
                throw new EmbeddingsUnavailableException("O modelo " + modelName + " gerou vetor de " + v.length
                        + " dimensões; o índice exige " + DIMENSIONS, null);
            }
        }
        return vectors;
    }

    /** pgvector text format: "[0.1,-0.2,...]". The database converts it with cast(... as vector). */
    public static String asText(float[] vector) {
        var text = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(vector[i]);
        }
        return text.append(']').toString();
    }
}
