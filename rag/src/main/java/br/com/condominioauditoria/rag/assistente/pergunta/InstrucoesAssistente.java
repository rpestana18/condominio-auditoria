package br.com.condominioauditoria.rag.assistente.pergunta;

import br.com.condominioauditoria.rag.indice.Localizacao;
import br.com.condominioauditoria.rag.indice.TrechoEncontrado;
import java.util.List;

/**
 * Instruções do assistente, em português, versionadas (ADR 0003, Decisão 1: "prompts versionados no rag; a versão vai
 * no registro de uso"). Mudou o texto? Mude {@link #VERSAO} no mesmo commit: é por ela que se rastreia mudança de
 * comportamento no relatório de uso.
 */
public final class InstrucoesAssistente {

    /** Vai em {@code UsoPergunta.versao_prompt}. */
    public static final String VERSAO = "2026-10-05.1";

    /** Marca obrigatória em todo parágrafo que transcreve número de documento (RF-04.14, ADR 0003 Q8). */
    public static final String MARCA_NAO_CONFERIDO = "(conforme o documento, não conferido)";

    private InstrucoesAssistente() {
    }

    public static String sistema() {
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

                Responda somente com o JSON do esquema pedido.""".formatted(MARCA_NAO_CONFERIDO);
    }

    /** Pergunta do usuário com os trechos recuperados e as regras de citação repetidas ao pé. */
    public static String pergunta(String perguntaUsuario, List<TrechoEncontrado> trechos) {
        StringBuilder texto = new StringBuilder();
        texto.append("TRECHOS DOS DOCUMENTOS DESTE CONDOMÍNIO\n");
        if (trechos.isEmpty()) {
            texto.append("(nenhum trecho foi encontrado para esta pergunta)\n");
        }
        for (TrechoEncontrado t : trechos) {
            texto.append("\n--- trechoId: ").append(t.trechoId())
                    .append("\n    documento: ").append(t.nomeArquivo())
                    .append(" (").append(t.categoria()).append(")")
                    .append("\n    localização: ").append(localizacao(t.localizacao()))
                    .append("\n    texto:\n").append(t.texto()).append('\n');
        }
        texto.append("\nPERGUNTA DO USUÁRIO\n").append(perguntaUsuario).append('\n');
        return texto.toString();
    }

    /** Motivo da recusa, para a única nova tentativa (RF-04.12). */
    public static String novaTentativa(String motivo) {
        return """
                A resposta anterior foi recusada pela conferência do sistema: %s

                Corrija e responda de novo, no mesmo esquema JSON. Se não conseguir sustentar a resposta nos trechos \
                citados, marque naoEncontrado = true em vez de arriscar.""".formatted(motivo);
    }

    private static String localizacao(Localizacao localizacao) {
        return switch (localizacao) {
            case Localizacao.Pagina l -> "página " + l.numero();
            case Localizacao.Planilha l -> "aba " + l.aba() + ", linhas " + l.linhaInicio() + " a " + l.linhaFim();
            case Localizacao.Paragrafos l -> (l.secao() == null || l.secao().isBlank() ? "" : l.secao() + ", ")
                    + "parágrafos " + l.inicio() + " a " + l.fim();
        };
    }
}
