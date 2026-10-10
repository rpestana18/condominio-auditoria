package br.com.condominioauditoria.rag.service;

import br.com.condominioauditoria.rag.config.properties.RagProperties;
import br.com.condominioauditoria.rag.search.FoundChunk;
import br.com.condominioauditoria.rag.service.ResponseSchema.DataComment;
import br.com.condominioauditoria.rag.service.ResponseSchema.ModelParagraph;
import br.com.condominioauditoria.rag.service.ResponseSchema.ModelResponse;
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
 * Check of the model's answer before it goes out (RF-04.2, RF-04.12 to RF-04.15; assistente.proto, "o que o rag valida
 * antes de devolver"):
 * <ol>
 * <li>every paragraph of "nos documentos" cites at least one chunk;</li>
 * <li>every cited chunk is among those retrieved for this question; and, since the search already filters by
 * condominium, a citation from another condominium does not even exist in the set;</li>
 * <li>every number written in a paragraph appears literally in one of the chunks that same paragraph cites, and the
 * paragraph carries the mark "(conforme o documento, não conferido)";</li>
 * <li>no conduct term (configurable list) outside literal quotation marks;</li>
 * <li>every chamada_id of "nos dados gravados" exists among the calls of this question, and the model's comment has
 * no number at all (the numbers are written by the rag).</li>
 * </ol>
 * Rejected? One retry with the reason; rejected again, NOT_FOUND.
 */
@Component
public class ResponseValidator {

    /** Any number: "12", "1.234,56", "2026-09-30" (each part), "7%". */
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");

    private final List<Pattern> conductTerms;

    @Autowired
    ResponseValidator(RagProperties properties) {
        this(properties.assistant().conductTerms());
    }

    public ResponseValidator(List<String> terms) {
        this.conductTerms = (terms == null ? List.<String>of() : terms).stream()
                .map(String::trim).filter(t -> !t.isEmpty())
                .map(t -> Pattern.compile("\\b" + Pattern.quote(t) + "\\b",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
                .toList();
    }

    /**
     * @param retrievedChunks chunks offered to the model in this question, by chunkId
     * @param callsMade chamada_id of the tools run in this question
     * @return empty when it passed; the reason (in Portuguese, for the retry) when rejected
     */
    public Optional<String> validate(ModelResponse response, Map<String, FoundChunk> retrievedChunks,
            Set<String> callsMade) {
        for (int i = 0; i < response.inDocuments().size(); i++) {
            Optional<String> error = validateParagraph(response.inDocuments().get(i), i + 1, retrievedChunks);
            if (error.isPresent()) {
                return error;
            }
        }
        for (DataComment dataItem : response.inStoredData()) {
            Optional<String> error = validateDataComment(dataItem, callsMade);
            if (error.isPresent()) {
                return error;
            }
        }
        return Optional.empty();
    }

    private Optional<String> validateParagraph(ModelParagraph paragraph, int sequence,
            Map<String, FoundChunk> chunks) {
        String text = paragraph.text() == null ? "" : paragraph.text();
        if (text.isBlank()) {
            return Optional.of("o parágrafo " + sequence + " de \"nos documentos\" está vazio");
        }
        if (paragraph.chunkIds().isEmpty()) {
            return Optional.of("o parágrafo " + sequence + " não cita nenhum trechoId; todo parágrafo precisa citar ao "
                    + "menos um trecho");
        }
        List<String> cited = new ArrayList<>();
        for (String id : paragraph.chunkIds()) {
            if (!chunks.containsKey(id)) {
                return Optional.of("o parágrafo " + sequence + " cita o trechoId \"" + id + "\", que não está entre os "
                        + "trechos recuperados para esta pergunta");
            }
            cited.add(chunks.get(id).text());
        }
        String citedText = String.join("\n", cited);
        List<String> numbers = numbers(text);
        if (!numbers.isEmpty()) {
            if (!text.contains(AssistantInstructions.UNVERIFIED_MARK)) {
                return Optional.of("o parágrafo " + sequence + " transcreve número de documento sem a marca \""
                        + AssistantInstructions.UNVERIFIED_MARK + "\"");
            }
            for (String number : numbers) {
                if (!citedText.contains(number)) {
                    return Optional.of("o número \"" + number + "\" do parágrafo " + sequence
                            + " não aparece em nenhum dos trechos que esse parágrafo cita; não escreva número que não "
                            + "esteja no trecho citado, ou use uma ferramenta de consulta");
                }
            }
        }
        return conduct(text, "o parágrafo " + sequence);
    }

    private Optional<String> validateDataComment(DataComment dataItem, Set<String> callsMade) {
        if (dataItem.callId() == null || !callsMade.contains(dataItem.callId())) {
            return Optional.of("\"nos dados gravados\" referencia o chamadaId \"" + dataItem.callId() + "\", que não "
                    + "existe entre as consultas feitas nesta pergunta");
        }
        String comment = dataItem.comment() == null ? "" : dataItem.comment();
        if (!numbers(comment).isEmpty()) {
            return Optional.of("o comentário do chamadaId " + dataItem.callId() + " tem número; os números do bloco "
                    + "\"nos dados gravados\" são escritos pelo sistema, o comentário vai sem número");
        }
        return conduct(comment, "o comentário do chamadaId " + dataItem.callId());
    }

    /** Conduct term only inside literal quotation marks (RF-04.15, ADR 0003 Q11). */
    private Optional<String> conduct(String text, String context) {
        boolean[] insideQuotes = quotes(text);
        for (Pattern term : conductTerms) {
            Matcher hit = term.matcher(text);
            while (hit.find()) {
                if (!insideQuotes[hit.start()]) {
                    return Optional.of(context + " usa a palavra \"" + hit.group() + "\" fora de aspas de citação "
                            + "literal; o sistema aponta indício com evidência e não escreve conclusão acusatória");
                }
            }
        }
        return Optional.empty();
    }

    /** Marks each position of the text as inside or outside a pair of quotes. */
    private static boolean[] quotes(String text) {
        boolean[] inside = new boolean[text.length()];
        boolean openStraight = false;
        boolean openCurly = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                openStraight = !openStraight;
                continue;
            }
            if (c == '“') {
                openCurly = true;
                continue;
            }
            if (c == '”') {
                openCurly = false;
                continue;
            }
            inside[i] = openStraight || openCurly;
        }
        return inside;
    }

    static List<String> numbers(String text) {
        List<String> hits = new ArrayList<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            hits.add(m.group());
        }
        return hits;
    }
}
