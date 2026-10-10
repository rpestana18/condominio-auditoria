package br.com.condominioauditoria.api.dto.response.assistant;

import java.util.List;

/** A paragraph of the answer and the numbers of the citations it relies on. */
public record DocumentParagraphResponse(
        String text,
        List<Integer> citations) {
}
