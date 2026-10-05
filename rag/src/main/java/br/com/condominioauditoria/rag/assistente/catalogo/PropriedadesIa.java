package br.com.condominioauditoria.rag.assistente.catalogo;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Catálogo de provedores e modelos de IA, só por configuração (bloco {@code condominio.ia} do application.yml,
 * ADR 0003, Decisão 1 e RF-09.6). O rag é quem conhece as implementações; o backend lê este catálogo pelo rpc
 * {@code ListarProvedores} para montar a tela de administração e validar o que é salvo.
 *
 * Acrescentar provedor de um tipo já implementado ({@code anthropic}, {@code ollama}) é só configuração; um tipo novo
 * é código e exige ADR.
 */
@ConfigurationProperties(prefix = "condominio.ia")
public record PropriedadesIa(List<ProvedorIa> provedores) {

    /** Para que serve o provedor: redigir respostas do chat ou gerar embeddings. */
    public enum Uso {
        RESPOSTAS, EMBEDDINGS
    }

    public record ProvedorIa(String codigo, String nome, String tipo, Uso uso, boolean local, boolean precisaChave,
            int dimensao, List<ModeloIa> modelos) {
    }

    /**
     * Preços em dólar por milhão de tokens, como texto decimal com ponto ("2.00"): protobuf não tem decimal exato e
     * o custo estimado do relatório é calculado no backend com BigDecimal. "0" para modelo local.
     */
    public record ModeloIa(String id, String nome, boolean padrao, String precoEntradaMilhaoUsd,
            String precoSaidaMilhaoUsd) {
    }
}
