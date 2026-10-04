package br.com.condominioauditoria.rag.indice;

import br.com.condominioauditoria.rag.indice.RepositorioIndice.Achado;
import br.com.condominioauditoria.rag.indice.RepositorioIndice.FiltrosBusca;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Busca nos documentos indexados (ADR 0003, Decisão 3).
 * <ul>
 * <li>PALAVRA: só a busca por palavra do PostgreSQL, sem IA.</li>
 * <li>HIBRIDA: palavra (até 50) + vetor (até 50) unidas por fusão de posições, k = 60. Se o Ollama estiver fora,
 * cai para PALAVRA e diz isso no resultado.</li>
 * </ul>
 * Os filtros (condomínio, vigência, exclusão lógica, categoria, período, arquivos) entram no WHERE das duas buscas.
 */
@Service
public class BuscaDocumentos {

    public static final int LIMITE_PADRAO = 10;
    public static final int LIMITE_MAXIMO = 50;
    /** Candidatos de cada busca antes da fusão. */
    static final int CANDIDATOS = 50;

    private static final Logger log = LoggerFactory.getLogger(BuscaDocumentos.class);

    public enum Modo {
        PALAVRA, HIBRIDA
    }

    public record Resultado(List<TrechoEncontrado> trechos, Modo modoUsado) {
    }

    private final RepositorioIndice repositorio;
    private final GeradorEmbeddings embeddings;

    BuscaDocumentos(RepositorioIndice repositorio, GeradorEmbeddings embeddings) {
        this.repositorio = repositorio;
        this.embeddings = embeddings;
    }

    /** {@code limite} 0 = padrão (10); acima de 50 é reduzido a 50. */
    public Resultado buscar(FiltrosBusca filtros, String texto, Modo modo, int limite) {
        int n = limite <= 0 ? LIMITE_PADRAO : Math.min(limite, LIMITE_MAXIMO);
        if (modo == Modo.PALAVRA) {
            return porPalavra(filtros, texto, n);
        }
        float[] vetor;
        try {
            vetor = embeddings.gerar(texto);
        } catch (EmbeddingsIndisponiveisException erro) {
            log.warn("Busca híbrida virou busca por palavra: {}", erro.getMessage());
            return porPalavra(filtros, texto, n);
        }
        List<UUID> palavras = repositorio.buscarPorPalavra(filtros, texto, CANDIDATOS).stream().map(Achado::id)
                .toList();
        List<UUID> vetores = repositorio.buscarPorVetor(filtros, vetor, embeddings.modelo(), CANDIDATOS);
        List<FusaoPosicoes.Pontuado> fundidos = FusaoPosicoes.fundir(List.of(palavras, vetores), FusaoPosicoes.K, n);
        return new Resultado(comPontuacao(fundidos.stream().map(FusaoPosicoes.Pontuado::id).toList(),
                pontuacoes(fundidos)), Modo.HIBRIDA);
    }

    private Resultado porPalavra(FiltrosBusca filtros, String texto, int n) {
        List<Achado> achados = repositorio.buscarPorPalavra(filtros, texto, n);
        Map<UUID, Double> notas = new HashMap<>();
        achados.forEach(a -> notas.put(a.id(), a.relevancia()));
        return new Resultado(comPontuacao(achados.stream().map(Achado::id).toList(), notas), Modo.PALAVRA);
    }

    private List<TrechoEncontrado> comPontuacao(List<UUID> ids, Map<UUID, Double> notas) {
        return repositorio.carregar(ids).stream().map(t -> t.comPontuacao(notas.get(t.trechoId()))).toList();
    }

    private static Map<UUID, Double> pontuacoes(List<FusaoPosicoes.Pontuado> fundidos) {
        Map<UUID, Double> notas = new HashMap<>();
        fundidos.forEach(p -> notas.put(p.id(), p.pontuacao()));
        return notas;
    }
}
