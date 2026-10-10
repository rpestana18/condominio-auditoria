package br.com.condominioauditoria.api.service.ai;

import br.com.condominioauditoria.contracts.assistant.v2.ListProvidersResponse;
import br.com.condominioauditoria.contracts.assistant.v2.ProviderModel;
import br.com.condominioauditoria.contracts.assistant.v2.Provider;
import br.com.condominioauditoria.contracts.assistant.v2.ProviderUsage;

/** Catalog like the rag's in delivery 3, plus an external embeddings provider (to test the Q12 rejection). */
public final class TestCatalog {

    private TestCatalog() {
    }

    public static ListProvidersResponse response(String publicKeyPem) {
        return ListProvidersResponse.newBuilder()
                .addProviders(Provider.newBuilder().setCode("anthropic").setName("Anthropic (Claude)")
                        .setType("anthropic").setUsage(ProviderUsage.PROVIDER_USAGE_ANSWERS).setRequiresKey(true)
                        .addModels(ProviderModel.newBuilder().setId("claude-sonnet-5-5").setName("Claude Sonnet 5.5")
                                .setIsDefault(true).setInputPricePerMillionUsd("2.00").setOutputPricePerMillionUsd("10.00"))
                        .addModels(ProviderModel.newBuilder().setId("claude-haiku-4-5").setName("Claude Haiku 4.5")
                                .setInputPricePerMillionUsd("1.00").setOutputPricePerMillionUsd("5.00")))
                .addProviders(Provider.newBuilder().setCode("ollama-local").setName("Ollama (local)")
                        .setType("ollama").setUsage(ProviderUsage.PROVIDER_USAGE_EMBEDDINGS).setLocal(true).setDimension(1024)
                        .addModels(ProviderModel.newBuilder().setId("bge-m3").setName("BGE-M3").setIsDefault(true)
                                .setInputPricePerMillionUsd("0").setOutputPricePerMillionUsd("0")))
                .addProviders(Provider.newBuilder().setCode("voyage").setName("Voyage (externo)")
                        .setType("voyage").setUsage(ProviderUsage.PROVIDER_USAGE_EMBEDDINGS).setRequiresKey(true)
                        .setDimension(1024)
                        .addModels(ProviderModel.newBuilder().setId("voyage-4").setName("Voyage 4").setIsDefault(true)
                                .setInputPricePerMillionUsd("0.06").setOutputPricePerMillionUsd("0")))
                .setPublicKeyPem(publicKeyPem)
                .build();
    }
}
