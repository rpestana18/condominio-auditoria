package br.com.condominioauditoria.rag.assistente.pergunta;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.rag.assistente.pergunta.EsquemaResposta.ComentarioDado;
import br.com.condominioauditoria.rag.assistente.pergunta.EsquemaResposta.ParagrafoModelo;
import br.com.condominioauditoria.rag.assistente.pergunta.EsquemaResposta.RespostaModelo;
import br.com.condominioauditoria.rag.indice.Localizacao;
import br.com.condominioauditoria.rag.indice.TrechoEncontrado;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Conferência da resposta antes de sair (RF-04.2, RF-04.12 a RF-04.15): citação inventada, número fora do trecho
 * citado, termo de conduta fora de aspas, parágrafo sem citação e chamada_id inexistente.
 */
class ValidadorRespostaTest {

    private static final String TRECHO_A = "11111111-1111-4111-8111-111111111111";
    private static final String TRECHO_B = "22222222-2222-4222-8222-222222222222";
    private static final String MARCA = InstrucoesAssistente.MARCA_NAO_CONFERIDO;

    private final ValidadorResposta validador =
            new ValidadorResposta(List.of("desvio", "fraude", "roubo", "culpa"));

    private final Map<String, TrechoEncontrado> trechos = Map.of(
            TRECHO_A, trecho(TRECHO_A, "A taxa de administração contratada é de R$ 1.234,56 por mês."),
            TRECHO_B, trecho(TRECHO_B, "O reajuste segue o IPCA, com data-base em janeiro."));

    @Test
    void passaQuandoOParagrafoCitaTrechoENumeroVemDoTrecho() {
        var resposta = resposta(List.of(new ParagrafoModelo(
                "O contrato registra taxa de administração de R$ 1.234,56 por mês " + MARCA + ".",
                List.of(TRECHO_A))), List.of());

        assertThat(validador.validar(resposta, trechos, Set.of())).isEmpty();
    }

    @Test
    void recusaCitacaoQueNaoEstaEntreOsTrechosRecuperados() {
        var resposta = resposta(List.of(new ParagrafoModelo("O contrato prevê reajuste anual.",
                List.of("99999999-9999-4999-8999-999999999999"))), List.of());

        assertThat(validador.validar(resposta, trechos, Set.of())).get().asString()
                .contains("não está entre os trechos recuperados");
    }

    @Test
    void recusaParagrafoSemNenhumaCitacao() {
        var resposta = resposta(List.of(new ParagrafoModelo("O contrato prevê reajuste anual.", List.of())),
                List.of());

        assertThat(validador.validar(resposta, trechos, Set.of())).get().asString()
                .contains("não cita nenhum trechoId");
    }

    @Test
    void recusaNumeroQueNaoEstaNoTrechoCitado() {
        var resposta = resposta(List.of(new ParagrafoModelo(
                "O contrato registra taxa de R$ 9.999,99 por mês " + MARCA + ".", List.of(TRECHO_A))), List.of());

        assertThat(validador.validar(resposta, trechos, Set.of())).get().asString()
                .contains("\"9.999,99\"").contains("não aparece em nenhum dos trechos");
    }

    @Test
    void recusaNumeroQueEstaEmOutroTrechoNaoCitadoNoParagrafo() {
        var resposta = resposta(List.of(new ParagrafoModelo(
                "A taxa é de R$ 1.234,56 " + MARCA + ".", List.of(TRECHO_B))), List.of());

        assertThat(validador.validar(resposta, trechos, Set.of())).get().asString().contains("\"1.234,56\"");
    }

    @Test
    void recusaNumeroSemAMarcaDeNaoConferido() {
        var resposta = resposta(List.of(new ParagrafoModelo(
                "A taxa de administração é de R$ 1.234,56 por mês.", List.of(TRECHO_A))), List.of());

        assertThat(validador.validar(resposta, trechos, Set.of())).get().asString()
                .contains("sem a marca");
    }

    @Test
    void recusaTermoDeCondutaForaDeAspas() {
        var resposta = resposta(List.of(new ParagrafoModelo(
                "O reajuste pelo IPCA indica desvio de finalidade.", List.of(TRECHO_B))), List.of());

        assertThat(validador.validar(resposta, trechos, Set.of())).get().asString()
                .contains("fora de aspas de citação literal");
    }

    @Test
    void aceitaTermoDeCondutaDentroDeAspasDeCitacaoLiteral() {
        var resposta = resposta(List.of(new ParagrafoModelo(
                "A ata usa a expressão \"suspeita de desvio\" ao tratar do reajuste pelo IPCA.", List.of(TRECHO_B))),
                List.of());

        assertThat(validador.validar(resposta, trechos, Set.of())).isEmpty();
    }

    @Test
    void recusaChamadaIdInexistente() {
        var resposta = resposta(List.of(), List.of(new ComentarioDado("c7", "Compare com o mês anterior.")));

        assertThat(validador.validar(resposta, trechos, Set.of("c1"))).get().asString()
                .contains("chamadaId \"c7\"").contains("não existe");
    }

    @Test
    void recusaComentarioComNumeroNoBlocoDeDadosGravados() {
        var resposta = resposta(List.of(), List.of(new ComentarioDado("c1", "O saldo caiu 12% no mês.")));

        assertThat(validador.validar(resposta, trechos, Set.of("c1"))).get().asString()
                .contains("tem número");
    }

    @Test
    void aceitaComentarioSemNumero() {
        var resposta = resposta(List.of(), List.of(new ComentarioDado("c1", "O saldo do fundo caiu no mês.")));

        assertThat(validador.validar(resposta, trechos, Set.of("c1"))).isEmpty();
    }

    private static RespostaModelo resposta(List<ParagrafoModelo> paragrafos, List<ComentarioDado> dados) {
        return new RespostaModelo(paragrafos, dados, false, "");
    }

    private static TrechoEncontrado trecho(String id, String texto) {
        return new TrechoEncontrado(UUID.fromString(id), UUID.randomUUID(), "contrato.pdf", "CONTRATO",
                new Localizacao.Pagina(3), texto, 1.0, "a".repeat(64));
    }
}
