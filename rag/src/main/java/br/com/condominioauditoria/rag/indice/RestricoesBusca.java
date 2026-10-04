package br.com.condominioauditoria.rag.indice;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai da pergunta as partes que, na busca por palavra, são obrigatórias ou proibidas: frases entre aspas e termos
 * negados com "-" (sintaxe do websearch_to_tsquery). Na busca híbrida, os candidatos do lado vetorial também têm de
 * respeitá-las; senão "salário -transporte" traria "Vale Transporte" pelos vetores.
 *
 * Simplificação: com "ou" entre frases, todas as frases ficam obrigatórias no lado vetorial (mais restritivo).
 */
public final class RestricoesBusca {

    /** Frase entre aspas, negada ou não. Aspas sem fechar não contam como frase. */
    private static final Pattern FRASE = Pattern.compile("(?<![^\\s])(-?)\"([^\"]*)\"");
    /** Termo negado: "-" no começo da palavra (não pega "IGP-M"). */
    private static final Pattern NEGADO = Pattern.compile("(?<![^\\s])-([^\\s\"-][^\\s\"]*)");

    private RestricoesBusca() {
    }

    /** Texto no formato do websearch_to_tsquery só com as restrições, ou null se a pergunta não tem nenhuma. */
    public static String extrair(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        List<String> partes = new ArrayList<>();
        var resto = new StringBuilder();
        Matcher frase = FRASE.matcher(texto);
        int fim = 0;
        while (frase.find()) {
            resto.append(texto, fim, frase.start()).append(' ');
            fim = frase.end();
            String conteudo = frase.group(2).replace('"', ' ').strip();
            if (!conteudo.isEmpty()) {
                partes.add(frase.group(1) + "\"" + conteudo + "\"");
            }
        }
        resto.append(texto.substring(fim));
        Matcher negado = NEGADO.matcher(resto);
        while (negado.find()) {
            partes.add("-" + negado.group(1));
        }
        return partes.isEmpty() ? null : String.join(" ", partes);
    }
}
