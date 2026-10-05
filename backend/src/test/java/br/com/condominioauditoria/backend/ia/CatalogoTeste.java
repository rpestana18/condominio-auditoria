package br.com.condominioauditoria.backend.ia;

import br.com.condominioauditoria.contratos.assistente.v1.ListarProvedoresResponse;
import br.com.condominioauditoria.contratos.assistente.v1.ModeloProvedor;
import br.com.condominioauditoria.contratos.assistente.v1.Provedor;
import br.com.condominioauditoria.contratos.assistente.v1.UsoProvedor;

/** Catálogo como o do rag na entrega 3, mais um provedor externo de embeddings (para testar a recusa do Q12). */
public final class CatalogoTeste {

    private CatalogoTeste() {
    }

    public static ListarProvedoresResponse resposta(String chavePublicaPem) {
        return ListarProvedoresResponse.newBuilder()
                .addProvedores(Provedor.newBuilder().setCodigo("anthropic").setNome("Anthropic (Claude)")
                        .setTipo("anthropic").setUso(UsoProvedor.USO_PROVEDOR_RESPOSTAS).setPrecisaChave(true)
                        .addModelos(ModeloProvedor.newBuilder().setId("claude-sonnet-5-5").setNome("Claude Sonnet 5.5")
                                .setPadrao(true).setPrecoEntradaMilhaoUsd("2.00").setPrecoSaidaMilhaoUsd("10.00"))
                        .addModelos(ModeloProvedor.newBuilder().setId("claude-haiku-4-5").setNome("Claude Haiku 4.5")
                                .setPrecoEntradaMilhaoUsd("1.00").setPrecoSaidaMilhaoUsd("5.00")))
                .addProvedores(Provedor.newBuilder().setCodigo("ollama-local").setNome("Ollama (local)")
                        .setTipo("ollama").setUso(UsoProvedor.USO_PROVEDOR_EMBEDDINGS).setLocal(true).setDimensao(1024)
                        .addModelos(ModeloProvedor.newBuilder().setId("bge-m3").setNome("BGE-M3").setPadrao(true)
                                .setPrecoEntradaMilhaoUsd("0").setPrecoSaidaMilhaoUsd("0")))
                .addProvedores(Provedor.newBuilder().setCodigo("voyage").setNome("Voyage (externo)")
                        .setTipo("voyage").setUso(UsoProvedor.USO_PROVEDOR_EMBEDDINGS).setPrecisaChave(true)
                        .setDimensao(1024)
                        .addModelos(ModeloProvedor.newBuilder().setId("voyage-4").setNome("Voyage 4").setPadrao(true)
                                .setPrecoEntradaMilhaoUsd("0.06").setPrecoSaidaMilhaoUsd("0")))
                .setChavePublicaPem(chavePublicaPem)
                .build();
    }
}
