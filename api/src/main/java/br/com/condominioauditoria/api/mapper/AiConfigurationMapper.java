package br.com.condominioauditoria.api.mapper;

import br.com.condominioauditoria.api.dto.request.ai.AiConfigurationRequest;
import br.com.condominioauditoria.api.dto.response.ai.AiAnswersResponse;
import br.com.condominioauditoria.api.dto.response.ai.AiAssistantResponse;
import br.com.condominioauditoria.api.dto.response.ai.AiConfigurationResponse;
import br.com.condominioauditoria.api.dto.response.ai.AiEmbeddingsResponse;
import br.com.condominioauditoria.api.dto.response.ai.AiModelResponse;
import br.com.condominioauditoria.api.dto.response.ai.AiProviderResponse;
import br.com.condominioauditoria.api.service.ai.AiCatalog.AiModel;
import br.com.condominioauditoria.api.service.ai.AiCatalog.AiProvider;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService.AnswersChange;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService.Change;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService.Effective;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService.EmbeddingsChange;
import java.math.BigDecimal;

/** AI configuration and provider catalog ↔ API DTOs. */
public final class AiConfigurationMapper {

    private AiConfigurationMapper() {
    }

    /** Never carries the key, not even encrypted: only whether it is registered and its last 4 characters. */
    public static AiConfigurationResponse toResponse(Effective effective) {
        var answers = effective.answers();
        var embeddings = effective.embeddings();
        return new AiConfigurationResponse(effective.generalMode(), new AiAssistantResponse(
                new AiAnswersResponse(answers.mode(), answers.effectiveMode(), answers.provider(), answers.model(),
                        answers.hasKey(), answers.hasKey() ? answers.keySuffix() : null),
                new AiEmbeddingsResponse(embeddings.mode(), embeddings.provider(), embeddings.model())),
                effective.updatedBy(), effective.updatedAt());
    }

    public static AiProviderResponse toResponse(AiProvider provider) {
        return new AiProviderResponse(provider.code(), provider.name(), provider.type(), provider.function(),
                provider.local(), provider.requiresKey(), provider.dimension(),
                provider.models().stream().map(AiConfigurationMapper::toResponse).toList());
    }

    public static AiModelResponse toResponse(AiModel model) {
        return new AiModelResponse(model.id(), model.name(), model.isDefault(),
                text(model.inputPricePerMillionUsd()), text(model.outputPricePerMillionUsd()));
    }

    /** The request as the service takes it; the caller has already checked that the assistant part exists. */
    public static Change toChange(AiConfigurationRequest request) {
        var answers = request.assistant().answers();
        var embeddings = request.assistant().embeddings();
        return new Change(request.generalMode(),
                answers == null ? null : new AnswersChange(answers.mode(), answers.provider(), answers.model(),
                        answers.key(), Boolean.TRUE.equals(answers.removeKey())),
                embeddings == null ? null
                        : new EmbeddingsChange(embeddings.mode(), embeddings.provider(), embeddings.model()));
    }

    private static String text(BigDecimal price) {
        return price == null ? "0" : price.toPlainString();
    }
}
