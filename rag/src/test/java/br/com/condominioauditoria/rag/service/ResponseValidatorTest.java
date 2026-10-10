package br.com.condominioauditoria.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.rag.search.FoundChunk;
import br.com.condominioauditoria.rag.search.Location;
import br.com.condominioauditoria.rag.service.ResponseSchema.DataComment;
import br.com.condominioauditoria.rag.service.ResponseSchema.ModelParagraph;
import br.com.condominioauditoria.rag.service.ResponseSchema.ModelResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Check of the answer before it goes out (RF-04.2, RF-04.12 to RF-04.15): invented citation, number outside the cited
 * chunk, conduct term outside quotes, paragraph without citation and nonexistent chamada_id.
 */
class ResponseValidatorTest {

    private static final String CHUNK_A = "11111111-1111-4111-8111-111111111111";
    private static final String CHUNK_B = "22222222-2222-4222-8222-222222222222";
    private static final String MARK = AssistantInstructions.UNVERIFIED_MARK;

    private final ResponseValidator validator =
            new ResponseValidator(List.of("desvio", "fraude", "roubo", "culpa"));

    private final Map<String, FoundChunk> chunks = Map.of(
            CHUNK_A, chunk(CHUNK_A, "A taxa de administração contratada é de R$ 1.234,56 por mês."),
            CHUNK_B, chunk(CHUNK_B, "O reajuste segue o IPCA, com data-base em janeiro."));

    @Test
    void passesWhenParagraphCitesChunkAndNumberComesFromChunk() {
        var response = response(List.of(new ModelParagraph(
                "O contrato registra taxa de administração de R$ 1.234,56 por mês " + MARK + ".",
                List.of(CHUNK_A))), List.of());

        assertThat(validator.validate(response, chunks, Set.of())).isEmpty();
    }

    @Test
    void rejectsCitationNotAmongRetrievedChunks() {
        var response = response(List.of(new ModelParagraph("O contrato prevê reajuste anual.",
                List.of("99999999-9999-4999-8999-999999999999"))), List.of());

        assertThat(validator.validate(response, chunks, Set.of())).get().asString()
                .contains("não está entre os trechos recuperados");
    }

    @Test
    void rejectsParagraphWithoutAnyCitation() {
        var response = response(List.of(new ModelParagraph("O contrato prevê reajuste anual.", List.of())),
                List.of());

        assertThat(validator.validate(response, chunks, Set.of())).get().asString()
                .contains("não cita nenhum trechoId");
    }

    @Test
    void rejectsNumberNotInCitedChunk() {
        var response = response(List.of(new ModelParagraph(
                "O contrato registra taxa de R$ 9.999,99 por mês " + MARK + ".", List.of(CHUNK_A))), List.of());

        assertThat(validator.validate(response, chunks, Set.of())).get().asString()
                .contains("\"9.999,99\"").contains("não aparece em nenhum dos trechos");
    }

    @Test
    void rejectsNumberFromAnotherChunkNotCitedInParagraph() {
        var response = response(List.of(new ModelParagraph(
                "A taxa é de R$ 1.234,56 " + MARK + ".", List.of(CHUNK_B))), List.of());

        assertThat(validator.validate(response, chunks, Set.of())).get().asString().contains("\"1.234,56\"");
    }

    @Test
    void rejectsNumberWithoutUnverifiedMark() {
        var response = response(List.of(new ModelParagraph(
                "A taxa de administração é de R$ 1.234,56 por mês.", List.of(CHUNK_A))), List.of());

        assertThat(validator.validate(response, chunks, Set.of())).get().asString()
                .contains("sem a marca");
    }

    @Test
    void rejectsConductTermOutsideQuotes() {
        var response = response(List.of(new ModelParagraph(
                "O reajuste pelo IPCA indica desvio de finalidade.", List.of(CHUNK_B))), List.of());

        assertThat(validator.validate(response, chunks, Set.of())).get().asString()
                .contains("fora de aspas de citação literal");
    }

    @Test
    void acceptsConductTermInsideLiteralQuotation() {
        var response = response(List.of(new ModelParagraph(
                "A ata usa a expressão \"suspeita de desvio\" ao tratar do reajuste pelo IPCA.", List.of(CHUNK_B))),
                List.of());

        assertThat(validator.validate(response, chunks, Set.of())).isEmpty();
    }

    @Test
    void rejectsUnknownCallId() {
        var response = response(List.of(), List.of(new DataComment("c7", "Compare com o mês anterior.")));

        assertThat(validator.validate(response, chunks, Set.of("c1"))).get().asString()
                .contains("chamadaId \"c7\"").contains("não existe");
    }

    @Test
    void rejectsCommentWithNumberInStoredDataBlock() {
        var response = response(List.of(), List.of(new DataComment("c1", "O saldo caiu 12% no mês.")));

        assertThat(validator.validate(response, chunks, Set.of("c1"))).get().asString()
                .contains("tem número");
    }

    @Test
    void acceptsCommentWithoutNumber() {
        var response = response(List.of(), List.of(new DataComment("c1", "O saldo do fundo caiu no mês.")));

        assertThat(validator.validate(response, chunks, Set.of("c1"))).isEmpty();
    }

    private static ModelResponse response(List<ModelParagraph> paragraphs, List<DataComment> data) {
        return new ModelResponse(paragraphs, data, false, "");
    }

    private static FoundChunk chunk(String id, String text) {
        return new FoundChunk(UUID.fromString(id), UUID.randomUUID(), "contrato.pdf", "CONTRATO",
                new Location.Page(3), text, 1.0, "a".repeat(64));
    }
}
