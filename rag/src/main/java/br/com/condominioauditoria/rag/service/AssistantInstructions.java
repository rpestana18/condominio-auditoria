package br.com.condominioauditoria.rag.service;

import br.com.condominioauditoria.rag.search.FoundChunk;
import br.com.condominioauditoria.rag.search.Location;
import java.util.List;

/**
 * Assistant instructions, in Portuguese, versioned (ADR 0003, Decision 1: "prompts versionados no rag; a versão vai no
 * registro de uso"). Changed the text? Change {@link #VERSION} in the same commit: it is how a change of behavior is
 * traced in the usage report.
 */
public final class AssistantInstructions {

    /** Goes into {@code UsoPergunta.versao_prompt}. */
    public static final String VERSION = "2026-10-05.1";

    /** Mandatory mark in every paragraph that transcribes a number from a document (RF-04.14, ADR 0003 Q8). */
    public static final String UNVERIFIED_MARK = "(conforme o documento, não conferido)";

    private AssistantInstructions() {
    }

    public static String system() {
        return """
                Você é o assistente de documentos de um condomínio brasileiro. Responde em português do Brasil, com \
                frases curtas e tom neutro.

                O que você pode usar:
                1. Os TRECHOS dos documentos indexados que vêm na pergunta, cada um com um trechoId.
                2. As ferramentas de consulta aos dados já gravados e conferidos pelo sistema.

                Regras que não têm exceção:
                - Nunca use conhecimento de fora: se a resposta não está nos trechos nem no resultado de uma \
                ferramenta, marque naoEncontrado = true.
                - Em "nosDocumentos", todo parágrafo precisa citar ao menos um trechoId, e só trechoIds que vieram \
                nesta pergunta. Não invente identificador.
                - Todo número que você escrever em "nosDocumentos" tem de aparecer, letra por letra, em um dos \
                trechos que o próprio parágrafo cita, e o parágrafo tem de terminar com a marca \
                "%s". Se um número não estiver nos trechos citados, não escreva o número: diga onde procurar ou use \
                uma ferramenta.
                - Para "quanto", "total", "média", "previsto × realizado" e qualquer conta, use as ferramentas. Você \
                nunca calcula nem transcreve número de dado gravado: quem escreve esses números é o sistema. Em \
                "nosDadosGravados" você só aponta o chamadaId da consulta e, se ajudar, um comentário SEM nenhum \
                número.
                - Não escreva nem sugira acusação. As palavras de conduta (desvio, fraude, roubo, culpa e parecidas) \
                só podem aparecer entre aspas, quando você está copiando o que o documento diz.
                - Diferença entre documento e dado gravado: documento é transcrição não conferida; dado gravado é o \
                que o sistema leu e conferiu.
                - Se faltar um tipo de documento para responder (por exemplo, o extrato de um mês), diga isso em \
                "sugestao", em uma frase.

                Responda somente com o JSON do esquema pedido.""".formatted(UNVERIFIED_MARK);
    }

    /** The user's question with the retrieved chunks and the citation rules repeated at the end. */
    public static String question(String userQuestion, List<FoundChunk> chunks) {
        StringBuilder text = new StringBuilder();
        text.append("TRECHOS DOS DOCUMENTOS DESTE CONDOMÍNIO\n");
        if (chunks.isEmpty()) {
            text.append("(nenhum trecho foi encontrado para esta pergunta)\n");
        }
        for (FoundChunk t : chunks) {
            text.append("\n--- trechoId: ").append(t.chunkId())
                    .append("\n    documento: ").append(t.fileName())
                    .append(" (").append(t.category()).append(")")
                    .append("\n    localização: ").append(location(t.location()))
                    .append("\n    texto:\n").append(t.text()).append('\n');
        }
        text.append("\nPERGUNTA DO USUÁRIO\n").append(userQuestion).append('\n');
        return text.toString();
    }

    /** Reason for the rejection, for the single retry (RF-04.12). */
    public static String retry(String reason) {
        return """
                A resposta anterior foi recusada pela conferência do sistema: %s

                Corrija e responda de novo, no mesmo esquema JSON. Se não conseguir sustentar a resposta nos trechos \
                citados, marque naoEncontrado = true em vez de arriscar.""".formatted(reason);
    }

    private static String location(Location location) {
        return switch (location) {
            case Location.Page l -> "página " + l.number();
            case Location.Sheet l -> "aba " + l.tab() + ", linhas " + l.startRow() + " a " + l.endRow();
            case Location.Paragraphs l -> (l.section() == null || l.section().isBlank() ? "" : l.section() + ", ")
                    + "parágrafos " + l.start() + " a " + l.end();
        };
    }
}
