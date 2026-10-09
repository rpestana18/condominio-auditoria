package br.com.condominioauditoria.rag.parser;

import br.com.condominioauditoria.rag.model.document.ReadDocument.Word;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Words at the same height on the page, from left to right. Used by every parser. */
public record TextLine(int page, double top, List<Word> words) {

    /** Maximum height difference for two words to be on the same line. */
    private static final double TOLERANCE = 2.0;

    public static List<TextLine> group(int page, List<Word> words) {
        List<Word> sorted = words.stream().sorted(Comparator.comparingDouble(Word::top)).toList();
        List<TextLine> lines = new ArrayList<>();
        List<Word> current = new ArrayList<>();
        double currentTop = Double.NaN;
        for (Word p : sorted) {
            if (!current.isEmpty() && p.top() - currentTop > TOLERANCE) {
                lines.add(create(page, currentTop, current));
                current = new ArrayList<>();
            }
            if (current.isEmpty()) {
                currentTop = p.top();
            }
            current.add(p);
        }
        if (!current.isEmpty()) {
            lines.add(create(page, currentTop, current));
        }
        return lines;
    }

    private static TextLine create(int page, double top, List<Word> words) {
        return new TextLine(page, top, words.stream().sorted(Comparator.comparingDouble(Word::x0)).toList());
    }

    public String text() {
        return words.stream().map(Word::text).collect(Collectors.joining(" "));
    }

    public Word first() {
        return words.getFirst();
    }

    public boolean contains(String word) {
        return words.stream().anyMatch(p -> p.text().equals(word));
    }
}
