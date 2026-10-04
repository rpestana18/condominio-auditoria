package br.com.condominioauditoria.rag.indice;

import br.com.condominioauditoria.rag.config.PropriedadesRag;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/**
 * Gera os vetores dos trechos e da pergunta pelo Ollama local (ADR 0003, Decisão 2: bge-m3, 1024 dimensões). O mesmo
 * modelo serve para indexar e para buscar; trocar de modelo obriga a reindexar.
 *
 * Nenhum texto sai da máquina: o Ollama roda na rede interna. Falha vira {@link EmbeddingsIndisponiveisException} com
 * motivo legível; quem chama decide (indexação = ERRO; busca híbrida = cai para a busca por palavra).
 */
@Component
public class GeradorEmbeddings {

    /** Dimensão fixa da coluna vector do índice. */
    public static final int DIMENSOES = 1024;

    private final EmbeddingModel modelo;
    private final String nomeModelo;
    private final String url;
    private final int lote;

    GeradorEmbeddings(EmbeddingModel modelo, @Value("${spring.ai.ollama.embedding.model}") String nomeModelo,
            @Value("${spring.ai.ollama.base-url}") String url, PropriedadesRag propriedades) {
        this.modelo = modelo;
        this.nomeModelo = nomeModelo;
        this.url = url;
        this.lote = Math.max(1, propriedades.embeddings().lote());
    }

    /** Nome do modelo configurado (ex.: bge-m3); vai em cada vetor gravado e no resultado da indexação. */
    public String modelo() {
        return nomeModelo;
    }

    /** Vazio ou igual ao configurado = ok. Outro modelo ainda não existe neste rag (catálogo é da entrega 2). */
    public boolean aceita(String modeloPedido) {
        return modeloPedido == null || modeloPedido.isBlank() || modeloPedido.equals(nomeModelo);
    }

    /** Um vetor por texto, na mesma ordem. */
    public List<float[]> gerar(List<String> textos) {
        List<float[]> vetores = new ArrayList<>(textos.size());
        for (int i = 0; i < textos.size(); i += lote) {
            List<String> parte = textos.subList(i, Math.min(textos.size(), i + lote));
            List<float[]> gerados = chamar(parte);
            if (gerados.size() != parte.size()) {
                throw new EmbeddingsIndisponiveisException("O Ollama devolveu " + gerados.size() + " vetores para "
                        + parte.size() + " trechos", null);
            }
            vetores.addAll(gerados);
        }
        return vetores;
    }

    public float[] gerar(String texto) {
        return gerar(List.of(texto)).getFirst();
    }

    private List<float[]> chamar(List<String> textos) {
        List<float[]> vetores;
        try {
            vetores = modelo.embed(textos);
        } catch (ResourceAccessException erro) {
            throw new EmbeddingsIndisponiveisException("O serviço de embeddings (Ollama) não respondeu em " + url
                    + ". Ele está rodando? (" + erro.getMessage() + ")", erro);
        } catch (RuntimeException erro) {
            String mensagem = String.valueOf(erro.getMessage());
            if (mensagem.contains("not found")) { // o Ollama responde 404 "model ... not found, try pulling it first"
                throw new EmbeddingsIndisponiveisException("O modelo " + nomeModelo
                        + " não está baixado no Ollama em " + url + " (" + mensagem + ")", erro);
            }
            throw new EmbeddingsIndisponiveisException("Falha ao gerar embeddings no Ollama: " + mensagem, erro);
        }
        for (float[] v : vetores) {
            if (v.length != DIMENSOES) {
                throw new EmbeddingsIndisponiveisException("O modelo " + nomeModelo + " gerou vetor de " + v.length
                        + " dimensões; o índice exige " + DIMENSOES, null);
            }
        }
        return vetores;
    }

    /** Formato de texto do pgvector: "[0.1,-0.2,...]". O banco converte com cast(... as vector). */
    static String comoTexto(float[] vetor) {
        var texto = new StringBuilder(vetor.length * 12).append('[');
        for (int i = 0; i < vetor.length; i++) {
            if (i > 0) {
                texto.append(',');
            }
            texto.append(vetor[i]);
        }
        return texto.append(']').toString();
    }
}
