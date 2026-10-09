package br.com.condominioauditoria.api.ia;

import br.com.condominioauditoria.api.config.properties.ApiProperties;
import br.com.condominioauditoria.api.grpc.ClienteAssistente;
import br.com.condominioauditoria.api.service.calculator.UsageCostCalculator.ModelPrice;
import br.com.condominioauditoria.contratos.assistente.v1.ListarProvedoresResponse;
import br.com.condominioauditoria.contratos.assistente.v1.ModeloProvedor;
import br.com.condominioauditoria.contratos.assistente.v1.Provedor;
import io.grpc.StatusRuntimeException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Catálogo de provedores e modelos de IA, lido do rag (ListarProvedores; RF-09.6, ADR 0003) e guardado em memória por
 * alguns minutos (condominio.rag.catalog-cache-seconds), para não chamar o rag a cada tela ou gravação. O catálogo
 * é o mesmo para todos os usuários; o token só serve para o rag aceitar a chamada.
 */
@Component
public class CatalogoIa {

    private static final Logger log = LoggerFactory.getLogger(CatalogoIa.class);
    static final String INDISPONIVEL = "Catálogo de provedores de IA indisponível: o serviço rag não respondeu."
            + " Nada foi gravado; tente de novo em instantes.";

    private final ClienteAssistente rag;
    private final Duration validade;
    private final Clock relogio;
    private volatile Lido guardado;

    @Autowired
    public CatalogoIa(ClienteAssistente rag, ApiProperties propriedades) {
        this(rag, Duration.ofSeconds(propriedades.rag().catalogCacheSeconds()), Clock.systemUTC());
    }

    CatalogoIa(ClienteAssistente rag, Duration validade, Clock relogio) {
        this.rag = rag;
        this.validade = validade;
        this.relogio = relogio;
    }

    /** Um modelo do catálogo. Preço em US$ por milhão de tokens (nulo se o rag mandou um texto que não é decimal). */
    public record ModeloIa(String id, String nome, boolean padrao, BigDecimal precoEntradaMilhaoUsd,
            BigDecimal precoSaidaMilhaoUsd) {
    }

    /** Um provedor do catálogo, na ordem do rag. dimensao só em embeddings. */
    public record ProvedorIa(String codigo, String nome, String tipo, FuncaoIa uso, boolean local,
            boolean precisaChave, Integer dimensao, List<ModeloIa> modelos) {

        public Optional<ModeloIa> modelo(String id) {
            return modelos.stream().filter(m -> m.id().equals(id)).findFirst();
        }

        /** O modelo marcado como padrão; sem marca, o primeiro. */
        public Optional<ModeloIa> modeloPadrao() {
            return modelos.stream().filter(ModeloIa::padrao).findFirst().or(() -> modelos.stream().findFirst());
        }
    }

    /** Catálogo e chave pública do rag (vazia = rag sem par de chaves: não dá para cadastrar chave). */
    public record Catalogo(List<ProvedorIa> provedores, String chavePublicaPem) {

        public Optional<ProvedorIa> provedor(String codigo) {
            return provedores.stream().filter(p -> p.codigo().equals(codigo)).findFirst();
        }

        /** Preços por "provedor/modelo", para o custo estimado do relatório de uso. */
        public Map<String, ModelPrice> precos() {
            Map<String, ModelPrice> precos = new LinkedHashMap<>();
            for (ProvedorIa p : provedores) {
                for (ModeloIa m : p.modelos()) {
                    if (m.precoEntradaMilhaoUsd() != null && m.precoSaidaMilhaoUsd() != null) {
                        precos.put(ModelPrice.key(p.codigo(), m.id()),
                                new ModelPrice(m.precoEntradaMilhaoUsd(), m.precoSaidaMilhaoUsd()));
                    }
                }
            }
            return precos;
        }
    }

    private record Lido(Catalogo catalogo, Instant em) {
    }

    /** Do cache, se ainda vale; senão pergunta ao rag. Rag fora do ar = {@link IaIndisponivelException} (503). */
    public Catalogo ler(String autorizacao) {
        Lido atual = guardado;
        Instant agora = relogio.instant();
        if (atual != null && agora.isBefore(atual.em().plus(validade))) {
            return atual.catalogo();
        }
        ListarProvedoresResponse resposta;
        try {
            resposta = rag.listarProvedores(autorizacao);
        } catch (StatusRuntimeException erro) {
            log.warn("Catálogo de IA: rag não respondeu ({}: {})", erro.getStatus().getCode(),
                    erro.getStatus().getDescription());
            throw new IaIndisponivelException(INDISPONIVEL);
        }
        Catalogo catalogo = converter(resposta);
        guardado = new Lido(catalogo, agora);
        return catalogo;
    }

    /** Preços para o custo estimado; vazio se o rag não respondeu (o relatório sai sem custo, não falha). */
    public Optional<Map<String, ModelPrice>> precos(String autorizacao) {
        try {
            return Optional.of(ler(autorizacao).precos());
        } catch (IaIndisponivelException erro) {
            return Optional.empty();
        }
    }

    static Catalogo converter(ListarProvedoresResponse resposta) {
        List<ProvedorIa> provedores = resposta.getProvedoresList().stream()
                .filter(p -> {
                    boolean conhecido = uso(p) != null;
                    if (!conhecido) {
                        log.warn("Catálogo de IA: provedor {} sem uso informado foi ignorado", p.getCodigo());
                    }
                    return conhecido;
                })
                .map(p -> new ProvedorIa(p.getCodigo(), p.getNome(), p.getTipo(), uso(p), p.getLocal(),
                        p.getPrecisaChave(), p.getDimensao() > 0 ? p.getDimensao() : null,
                        p.getModelosList().stream().map(CatalogoIa::modelo).toList()))
                .toList();
        return new Catalogo(provedores, resposta.getChavePublicaPem());
    }

    private static FuncaoIa uso(Provedor p) {
        return switch (p.getUso()) {
            case USO_PROVEDOR_RESPOSTAS -> FuncaoIa.RESPOSTAS;
            case USO_PROVEDOR_EMBEDDINGS -> FuncaoIa.EMBEDDINGS;
            default -> null;
        };
    }

    private static ModeloIa modelo(ModeloProvedor m) {
        return new ModeloIa(m.getId(), m.getNome(), m.getPadrao(), preco(m.getPrecoEntradaMilhaoUsd()),
                preco(m.getPrecoSaidaMilhaoUsd()));
    }

    /** Texto decimal exato ("2.00"); vazio = 0 (modelo local); fora do formato = nulo (custo não calculado). */
    static BigDecimal preco(String texto) {
        if (texto == null || texto.isBlank()) {
            return BigDecimal.ZERO;
        }
        if (!texto.strip().matches("[0-9]+(\\.[0-9]+)?")) {
            log.warn("Catálogo de IA: preço fora do formato decimal ignorado: {}", texto);
            return null;
        }
        return new BigDecimal(texto.strip());
    }
}
