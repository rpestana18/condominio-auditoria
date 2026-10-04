package br.com.condominioauditoria.backend.auditoria;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Termos de conduta proibidos nos textos gerados pelo sistema fora de citação literal (RF-04.15; lista inicial
 * decidida pelo usuário em 03/10/2026, Q11). A lista só cresce por decisão do usuário. Usada nos testes de telas,
 * avisos, achados e exportações (RF-03.1.12).
 */
public final class TermosConduta {

    public static final List<String> LISTA = List.of("desvio", "fraude", "roubo", "culpa");

    private static final Pattern TERMO = Pattern.compile("\\b(desvios?|fraudes?|roubos?|culpas?)\\b");

    private TermosConduta() {
    }

    /** Ocorrências dos termos da lista, no singular ou no plural (sem acento, sem diferença de maiúsculas). */
    public static List<String> encontrados(String texto) {
        if (texto == null || texto.isEmpty()) {
            return List.of();
        }
        String normal = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        Matcher m = TERMO.matcher(normal);
        java.util.ArrayList<String> achados = new java.util.ArrayList<>();
        while (m.find()) {
            achados.add(m.group());
        }
        return List.copyOf(achados);
    }
}
