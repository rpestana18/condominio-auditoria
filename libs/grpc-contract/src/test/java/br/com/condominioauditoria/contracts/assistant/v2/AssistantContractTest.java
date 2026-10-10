package br.com.condominioauditoria.contracts.assistant.v2;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Compatibility guard of contracts/grpc/assistant/v2. The v2 only accepts additions: new rpcs, messages and fields. If
 * this test fails because the number or type of an existing field changed, the change needs a v3 (CLAUDE.md).
 */
class AssistantContractTest {

    @Test
    void serviceRpcs() {
        assertThat(AssistantGrpc.getSearchMethod().getType()).isEqualTo(MethodType.UNARY);
        assertThat(AssistantGrpc.getAskMethod().getType()).isEqualTo(MethodType.SERVER_STREAMING);
        assertThat(AssistantGrpc.getListProvidersMethod().getType()).isEqualTo(MethodType.UNARY);
        assertThat(AssistantGrpc.getServiceDescriptor().getMethods()).hasSize(3);
    }

    @Test
    void deliveryOneFieldsDoNotChange() {
        assertThat(fields(SearchRequest.getDescriptor())).containsExactly(
                Map.entry("condominium_id", 1), Map.entry("text", 2), Map.entry("mode", 3),
                Map.entry("filters", 4), Map.entry("limit", 5), Map.entry("embedding_model", 6));
        assertThat(fields(SearchFilters.getDescriptor())).containsExactly(
                Map.entry("categories", 1), Map.entry("date_from", 2), Map.entry("date_to", 3),
                Map.entry("file_ids", 4));
        assertThat(fields(SearchResponse.getDescriptor())).containsExactly(
                Map.entry("chunks", 1), Map.entry("mode_used", 2));
        assertThat(fields(IndexedChunk.getDescriptor())).containsExactly(
                Map.entry("chunk_id", 1), Map.entry("file_id", 2), Map.entry("file_name", 3),
                Map.entry("category", 4), Map.entry("location", 5), Map.entry("text", 6),
                Map.entry("score", 7), Map.entry("sha256", 8));
        assertThat(fields(ChunkLocation.getDescriptor())).containsExactly(
                Map.entry("page", 1), Map.entry("sheet", 2), Map.entry("paragraphs", 3));
    }

    @Test
    void deliveryThreeFields() {
        assertThat(fields(AskRequest.getDescriptor())).containsExactly(
                Map.entry("condominium_id", 1), Map.entry("question", 2), Map.entry("history", 3),
                Map.entry("filters", 4), Map.entry("configuration", 5), Map.entry("chunk_limit", 6));
        assertThat(AskRequest.getDescriptor().findFieldByName("filters").getMessageType())
                .isEqualTo(SearchFilters.getDescriptor());
        assertThat(fields(AskConfiguration.getDescriptor())).containsExactly(
                Map.entry("provider", 1), Map.entry("model", 2), Map.entry("encrypted_key", 3),
                Map.entry("embedding_model", 4), Map.entry("search_mode", 5));
        assertThat(AskConfiguration.getDescriptor().findFieldByName("encrypted_key").getType())
                .isEqualTo(FieldDescriptor.Type.BYTES);

        Descriptor event = AskEvent.getDescriptor();
        assertThat(event.getOneofs()).hasSize(1);
        assertThat(event.getOneofs().getFirst().getFields()).extracting(FieldDescriptor::getName)
                .containsExactly("progress", "answer");

        assertThat(fields(Answer.getDescriptor())).containsExactly(
                Map.entry("outcome", 1), Map.entry("in_documents", 2), Map.entry("in_stored_data", 3),
                Map.entry("cited_chunks", 4), Map.entry("suggestion", 5), Map.entry("warning", 6),
                Map.entry("usage", 7));
        assertThat(Answer.getDescriptor().findFieldByName("cited_chunks").getMessageType())
                .isEqualTo(IndexedChunk.getDescriptor());
        assertThat(fields(AskUsage.getDescriptor())).containsExactly(
                Map.entry("input_tokens", 1), Map.entry("output_tokens", 2), Map.entry("provider", 3),
                Map.entry("model", 4), Map.entry("prompt_version", 5), Map.entry("attempts", 6));
        assertThat(fields(ListProvidersResponse.getDescriptor())).containsExactly(
                Map.entry("providers", 1), Map.entry("public_key_pem", 2));
        assertThat(fields(ProviderModel.getDescriptor())).containsExactly(
                Map.entry("id", 1), Map.entry("name", 2), Map.entry("is_default", 3),
                Map.entry("input_price_per_million_usd", 4), Map.entry("output_price_per_million_usd", 5));
    }

    @Test
    void pricesAndMoneyAreNeverFloatingPoint() {
        // Prices and formatted amounts are text; the only double in the contract is IndexedChunk.score (relevance).
        for (Descriptor message : AssistantProto.getDescriptor().getMessageTypes()) {
            for (FieldDescriptor field : message.getFields()) {
                if (field.getJavaType() == FieldDescriptor.JavaType.DOUBLE
                        || field.getJavaType() == FieldDescriptor.JavaType.FLOAT) {
                    assertThat(message.getName() + "." + field.getName()).isEqualTo("IndexedChunk.score");
                }
            }
        }
    }

    private static Map<String, Integer> fields(Descriptor descriptor) {
        Map<String, Integer> fields = new LinkedHashMap<>();
        descriptor.getFields().forEach(field -> fields.put(field.getName(), field.getNumber()));
        return fields;
    }
}
