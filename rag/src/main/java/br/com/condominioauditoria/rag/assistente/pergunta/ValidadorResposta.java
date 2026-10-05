package br.com.condominioauditoria.rag.assistente.pergunta;

import br.com.condominioauditoria.rag.assistente.pergunta.EsquemaResposta.ComentarioDado;
import br.com.condominioauditoria.rag.assistente.pergunta.EsquemaResposta.ParagrafoModelo;
import br.com.condominioauditoria.rag.assistente.pergunta.EsquemaResposta.RespostaModelo;
import br.com.condominioauditoria.rag.config.PropriedadesRag;
import br.com.condominioauditoria.rag.indice.TrechoEncontrado;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Conferência da resposta do modelo antes de ela sair (RF-04.2, RF-04.12 a RF-04.15; assistente.proto, "o que o rag
 * valida antes de devolver"):
 * <ol>
 * <li>todo parágrafo de "nos documentos" cita ao menos um trecho;</li>
 * <li>todo trecho citado está entre os recuperados para esta pergunta — e, como a busca já filtra por condomínio,
 * citação de outro condomínio nem chega a existir no conjunto;</li>
 * <li>todo número escrito num parágrafo aparece literalmente em um dos trechos que aquele mesmo parágrafo cita, e o
 * parágrafo traz a marca "(conforme o documento, não conferido)";</li>
 * <li>nenhum termo de conduta (lista configurável) fora de aspas de citação literal;</li>
 * <li>todo chamada_id de "nos dados gravados" existe entre as chamadas desta pergunta, e o comentário do modelo não
 * tem número nenhum (os números são escritos pelo rag).</li>
 * </ol>
 * Reprovou? Uma nova tentativa com o motivo; reprovou de novo, NAO_ENCONTRADA.
 */
@Component
public class ValidadorResposta {

    /** Qualquer número: "12", "1.234,56", "2026-09-30" (cada parte), "7%". */
    private static final Pattern NUMERO = Pattern.compile("\\d+(?:[.,]\\d+)*");

    private final List<Pattern> termosConduta;

    @Autowired
    ValidadorResposta(PropriedadesRag propriedades) {
        this(propriedades.assistente().termosConduta());
    }

    public ValidadorResposta(List<String> termos) {
        this.termosConduta = (termos == null ? List.<String>of() : termos).stream()
                .map(String::trim).filter(t -> !t.isEmpty())
                .map(t -> Pattern.compile("\\b" + Pattern.quote(t) + "\\b",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
                .toList();
    }

    /**
     * @param trechosRecuperados trechos oferecidos ao modelo nesta pergunta, por trechoId
     * @param chamadasFeitas chamada_id das ferramentas executadas nesta pergunta
     * @return vazio quando passou; o motivo (em português, para a nova tentativa) quando reprovou
     */
    public Optional<String> validar(RespostaModelo resposta, Map<String, TrechoEncontrado> trechosRecuperados,
            Set<String> chamadasFeitas) {
        for (int i = 0; i < resposta.nosDocumentos().size(); i++) {
            Optional<String> erro = validarParagrafo(resposta.nosDocumentos().get(i), i + 1, trechosRecuperados);
            if (erro.isPresent()) {
                return erro;
            }
        }
        for (ComentarioDado dado : resposta.nosDadosGravados()) {
            Optional<String> erro = validarDado(dado, chamadasFeitas);
            if (erro.isPresent()) {
                return erro;
            }
        }
        return Optional.empty();
    }

    private Optional<String> validarParagrafo(ParagrafoModelo paragrafo, int ordem,
            Map<String, TrechoEncontrado> trechos) {
        String texto = paragrafo.texto() == null ? "" : paragrafo.texto();
        if (texto.isBlank()) {
            return Optional.of("o parágrafo " + ordem + " de \"nos documentos\" está vazio");
        }
        if (paragrafo.trechoIds().isEmpty()) {
            return Optional.of("o parágrafo " + ordem + " não cita nenhum trechoId; todo parágrafo precisa citar ao "
                    + "menos um trecho");
        }
        List<String> citados = new ArrayList<>();
        for (String id : paragrafo.trechoIds()) {
            if (!trechos.containsKey(id)) {
                return Optional.of("o parágrafo " + ordem + " cita o trechoId \"" + id + "\", que não está entre os "
                        + "trechos recuperados para esta pergunta");
            }
            citados.add(trechos.get(id).texto());
        }
        String apoio = String.join("\n", citados);
        List<String> numeros = numeros(texto);
        if (!numeros.isEmpty()) {
            if (!texto.contains(InstrucoesAssistente.MARCA_NAO_CONFERIDO)) {
                return Optional.of("o parágrafo " + ordem + " transcreve número de documento sem a marca \""
                        + InstrucoesAssistente.MARCA_NAO_CONFERIDO + "\"");
            }
            for (String numero : numeros) {
                if (!apoio.contains(numero)) {
                    return Optional.of("o número \"" + numero + "\" do parágrafo " + ordem + " não aparece em nenhum "
                            + "dos trechos que esse parágrafo cita; não escreva número que não esteja no trecho "
                            + "citado, ou use uma ferramenta de consulta");
                }
            }
        }
        return conduta(texto, "o parágrafo " + ordem);
    }

    private Optional<String> validarDado(ComentarioDado dado, Set<String> chamadasFeitas) {
        if (dado.chamadaId() == null || !chamadasFeitas.contains(dado.chamadaId())) {
            return Optional.of("\"nos dados gravados\" referencia o chamadaId \"" + dado.chamadaId() + "\", que não "
                    + "existe entre as consultas feitas nesta pergunta");
        }
        String comentario = dado.comentario() == null ? "" : dado.comentario();
        if (!numeros(comentario).isEmpty()) {
            return Optional.of("o comentário do chamadaId " + dado.chamadaId() + " tem número; os números do bloco "
                    + "\"nos dados gravados\" são escritos pelo sistema, o comentário vai sem número");
        }
        return conduta(comentario, "o comentário do chamadaId " + dado.chamadaId());
    }

    /** Termo de conduta só entre aspas de citação literal (RF-04.15, ADR 0003 Q11). */
    private Optional<String> conduta(String texto, String onde) {
        boolean[] dentroDeAspas = aspas(texto);
        for (Pattern termo : termosConduta) {
            Matcher achado = termo.matcher(texto);
            while (achado.find()) {
                if (!dentroDeAspas[achado.start()]) {
                    return Optional.of(onde + " usa a palavra \"" + achado.group() + "\" fora de aspas de citação "
                            + "literal; o sistema aponta indício com evidência e não escreve conclusão acusatória");
                }
            }
        }
        return Optional.empty();
    }

    /** Marca cada posição do texto como dentro ou fora de um par de aspas. */
    private static boolean[] aspas(String texto) {
        boolean[] dentro = new boolean[texto.length()];
        boolean abertaReta = false;
        boolean abertaCurva = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '"') {
                abertaReta = !abertaReta;
                continue;
            }
            if (c == '“') {
                abertaCurva = true;
                continue;
            }
            if (c == '”') {
                abertaCurva = false;
                continue;
            }
            dentro[i] = abertaReta || abertaCurva;
        }
        return dentro;
    }

    static List<String> numeros(String texto) {
        List<String> achados = new ArrayList<>();
        Matcher m = NUMERO.matcher(texto);
        while (m.find()) {
            achados.add(m.group());
        }
        return achados;
    }
}
