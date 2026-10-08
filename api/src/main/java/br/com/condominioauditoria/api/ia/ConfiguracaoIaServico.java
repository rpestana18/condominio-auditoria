package br.com.condominioauditoria.api.ia;

import br.com.condominioauditoria.api.ia.CatalogoIa.Catalogo;
import br.com.condominioauditoria.api.ia.CatalogoIa.ModeloIa;
import br.com.condominioauditoria.api.ia.CatalogoIa.ProvedorIa;
import br.com.condominioauditoria.api.modulo.ModoIa;
import br.com.condominioauditoria.api.modulo.Modulos;
import br.com.condominioauditoria.api.modulo.PedidoInvalidoException;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Configuração de IA do condomínio (RF-09.1, RF-09.2, RF-09.6; ADR 0003, Decisão 4 e Sub-decisão 4.1 A).
 *
 * <ul>
 * <li>Modo geral: sem linha = MCP_EXTERNO (padrão do piloto).</li>
 * <li>Respostas do Assistente (chat): modo nulo = herda o modo geral; provedor, modelo e chave cifrada.</li>
 * <li>Embeddings do Assistente: só LOCAL (provedor local do catálogo) ou DESLIGADO nesta fase (Q12); sem linha =
 * LOCAL com ollama-local/bge-m3.</li>
 * </ul>
 *
 * A chave de API chega aberta só no PUT, é cifrada na hora com a chave pública do rag e nunca volta, nunca vai para log
 * nem para a trilha (só "chave trocada" e os 4 últimos caracteres). Quem altera (ADMIN) é verificado na API.
 */
@Service
public class ConfiguracaoIaServico {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracaoIaServico.class);

    public static final ModoIa MODO_GERAL_PADRAO = ModoIa.MCP_EXTERNO;
    public static final ModoIa MODO_EMBEDDINGS_PADRAO = ModoIa.LOCAL;
    public static final String PROVEDOR_EMBEDDINGS_PADRAO = "ollama-local";
    public static final String MODELO_EMBEDDINGS_PADRAO = "bge-m3";
    static final int CHAVE_MINIMO = 8;
    static final int CHAVE_MAXIMO = 500;

    static final String SEM_CHAVE_PUBLICA = "O serviço rag está sem chave pública para cifrar a chave de IA."
            + " Nada foi gravado; avise o suporte da instalação.";

    private final ConfiguracaoIaRepository configuracoes;
    private final EventoConfiguracaoIaRepository eventos;
    private final CatalogoIa catalogo;
    private final Modulos modulos;
    private final TransactionOperations transacao;

    ConfiguracaoIaServico(ConfiguracaoIaRepository configuracoes, EventoConfiguracaoIaRepository eventos,
            CatalogoIa catalogo, Modulos modulos, TransactionOperations transacao) {
        this.configuracoes = configuracoes;
        this.eventos = eventos;
        this.catalogo = catalogo;
        this.modulos = modulos;
        this.transacao = transacao;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Leitura
    // ---------------------------------------------------------------------------------------------------------------

    /** Configuração efetiva, com os padrões aplicados. atualizadoPor/Em nulos = nunca gravada. */
    public record Efetiva(ModoIa modoGeral, Respostas respostas, Embeddings embeddings, String atualizadoPor,
            Instant atualizadoEm) {
    }

    /** Respostas do Assistente. modo nulo = herda o geral. A chave só existe cifrada (o backend não a lê). */
    public record Respostas(ModoIa modo, ModoIa modoEfetivo, String provedor, String modelo, byte[] chaveCifrada,
            String chaveFinal) {

        public boolean chaveCadastrada() {
            return chaveCifrada != null && chaveCifrada.length > 0;
        }

        /** Chat na tela: modo efetivo API_KEY com chave cadastrada (e provedor escolhido). */
        public boolean chatDisponivel() {
            return modoEfetivo == ModoIa.API_KEY && chaveCadastrada() && provedor != null;
        }

        @Override
        public String toString() {
            return "Respostas[" + modo + "/" + modoEfetivo + ", " + provedor + "/" + modelo + ", chave "
                    + (chaveCadastrada() ? "cadastrada" : "ausente") + "]";
        }
    }

    public record Embeddings(ModoIa modo, String provedor, String modelo) {
    }

    /** Modo efetivo do Assistente para a tela (RF-04.16); nulo com o módulo desligado. Nunca traz chave. */
    public record ContextoAssistente(ModoIa modoRespostas, ModoIa modoEmbeddings, boolean chatDisponivel) {
    }

    public Efetiva ler(UUID condominioId) {
        return efetiva(configuracoes.findByCondominioId(condominioId));
    }

    public ContextoAssistente contexto(UUID condominioId) {
        if (!modulos.ligado(condominioId, Modulos.ASSISTENTE)) {
            return null;
        }
        Efetiva e = ler(condominioId);
        return new ContextoAssistente(e.respostas().modoEfetivo(), e.embeddings().modo(),
                e.respostas().chatDisponivel());
    }

    static Efetiva efetiva(List<ConfiguracaoIa> linhas) {
        Optional<ConfiguracaoIa> geral = linha(linhas, null, FuncaoIa.RESPOSTAS);
        Optional<ConfiguracaoIa> respostas = linha(linhas, Modulos.ASSISTENTE, FuncaoIa.RESPOSTAS);
        Optional<ConfiguracaoIa> embeddings = linha(linhas, Modulos.ASSISTENTE, FuncaoIa.EMBEDDINGS);
        ModoIa modoGeral = geral.map(ConfiguracaoIa::getModo).orElse(MODO_GERAL_PADRAO);
        ModoIa modoRespostas = respostas.map(ConfiguracaoIa::getModo).orElse(null);
        Respostas r = new Respostas(modoRespostas, modoRespostas == null ? modoGeral : modoRespostas,
                respostas.map(ConfiguracaoIa::getProvedor).orElse(null),
                respostas.map(ConfiguracaoIa::getModelo).orElse(null),
                respostas.map(ConfiguracaoIa::getChaveCifrada).orElse(null),
                respostas.map(ConfiguracaoIa::getChaveFinal).orElse(null));
        Embeddings e = embeddings.map(l -> new Embeddings(l.getModo(), l.getProvedor(), l.getModelo()))
                .orElse(new Embeddings(MODO_EMBEDDINGS_PADRAO, PROVEDOR_EMBEDDINGS_PADRAO, MODELO_EMBEDDINGS_PADRAO));
        Optional<ConfiguracaoIa> ultima = linhas.stream().filter(l -> l.getAtualizadoEm() != null)
                .max(Comparator.comparing(ConfiguracaoIa::getAtualizadoEm));
        return new Efetiva(modoGeral, r, e, ultima.map(ConfiguracaoIa::getAtualizadoPor).orElse(null),
                ultima.map(ConfiguracaoIa::getAtualizadoEm).orElse(null));
    }

    private static Optional<ConfiguracaoIa> linha(List<ConfiguracaoIa> linhas, String modulo, FuncaoIa funcao) {
        return linhas.stream().filter(l -> Objects.equals(l.getModulo(), modulo) && l.getFuncao() == funcao)
                .findFirst();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Gravação
    // ---------------------------------------------------------------------------------------------------------------

    /** PUT /condominios/{id}/ia. A chave é só de escrita: nulo mantém a guardada; removerChave apaga. */
    public record Pedido(ModoIa modoGeral, PedidoRespostas respostas, PedidoEmbeddings embeddings) {
    }

    public record PedidoRespostas(ModoIa modo, String provedor, String modelo, String chave, boolean removerChave) {

        /** Nunca mostra a chave (nem em log de erro do Spring). */
        @Override
        public String toString() {
            return "PedidoRespostas[" + modo + ", " + provedor + "/" + modelo + ", chave "
                    + (chave == null ? "não enviada" : "enviada") + ", removerChave " + removerChave + "]";
        }
    }

    public record PedidoEmbeddings(ModoIa modo, String provedor, String modelo) {
    }

    /** Valores resolvidos de uma função, para comparar com o que vale e para a trilha. */
    private record Valores(ModoIa modo, String provedor, String modelo) {
    }

    /**
     * Valida tudo (422 com todos os motivos), cifra a chave nova fora da transação e grava numa transação só, com um
     * evento na trilha para cada função que mudou. Pedir o que já vale não grava nada.
     */
    public Efetiva gravar(UUID condominioId, Pedido pedido, String usuario, String autorizacao) {
        if (pedido == null || pedido.modoGeral() == null || pedido.respostas() == null || pedido.embeddings() == null
                || pedido.embeddings().modo() == null) {
            throw new PedidoInvalidoException("Informe o modo geral e a configuração do Assistente (respostas e"
                    + " embeddings, com o modo dos embeddings)");
        }
        Efetiva atual = ler(condominioId);
        PedidoRespostas pr = pedido.respostas();
        PedidoEmbeddings pe = pedido.embeddings();
        String chave = pr.chave() == null ? null : pr.chave().strip();
        List<String> motivos = new ArrayList<>();

        if (pedido.modoGeral() == ModoIa.LOCAL) {
            motivos.add("O modo geral LOCAL ainda não está disponível nesta fase: escolha MCP_EXTERNO, API_KEY ou"
                    + " DESLIGADO.");
        }
        if (pr.modo() == ModoIa.LOCAL) {
            motivos.add("O modo LOCAL para as respostas do Assistente ainda não está disponível nesta fase (previsto,"
                    + " sem provedor).");
        }
        ModoIa efetivo = pr.modo() == null ? pedido.modoGeral() : pr.modo();
        if (chave != null && pr.removerChave()) {
            motivos.add("Informe uma chave nova ou peça para remover a chave guardada, não os dois.");
        }
        if (chave != null && (chave.length() < CHAVE_MINIMO || chave.length() > CHAVE_MAXIMO)) {
            motivos.add("A chave de API deve ter de " + CHAVE_MINIMO + " a " + CHAVE_MAXIMO + " caracteres.");
        }
        if (pe.modo() != ModoIa.LOCAL && pe.modo() != ModoIa.DESLIGADO) {
            motivos.add("Para embeddings só são aceitos os modos LOCAL ou DESLIGADO nesta fase: só embeddings locais"
                    + " são permitidos, sem enviar texto para fora (Q12).");
        }

        String provedorR = textoOuNulo(pr.provedor());
        String modeloR = textoOuNulo(pr.modelo());
        String provedorE = pe.modo() == ModoIa.LOCAL ? textoOuNulo(pe.provedor()) : null;
        String modeloE = pe.modo() == ModoIa.LOCAL ? textoOuNulo(pe.modelo()) : null;
        boolean precisaCatalogo = efetivo == ModoIa.API_KEY || provedorR != null || pe.modo() == ModoIa.LOCAL
                || chave != null;
        Catalogo cat = precisaCatalogo ? catalogo.ler(autorizacao) : null; // rag fora do ar = 503, nada gravado

        // Respostas: provedor obrigatório em API_KEY; modelo nulo = padrão do provedor
        if (provedorR == null && modeloR != null) {
            motivos.add("Informe o provedor do modelo de respostas '" + modeloR + "'.");
        }
        if (efetivo == ModoIa.API_KEY && provedorR == null) {
            motivos.add("No modo API_KEY, escolha o provedor das respostas no catálogo.");
        }
        if (provedorR != null) {
            modeloR = validarProvedor(cat, provedorR, modeloR, FuncaoIa.RESPOSTAS, false, motivos);
        }
        boolean chaveDepois = chave != null || (!pr.removerChave() && atual.respostas().chaveCadastrada());
        if (efetivo == ModoIa.API_KEY && !chaveDepois) {
            motivos.add("O modo API_KEY exige a chave de API do condomínio: informe a chave.");
        }

        // Embeddings: LOCAL exige provedor local do catálogo; DESLIGADO não tem provedor nem modelo
        if (pe.modo() == ModoIa.LOCAL) {
            if (provedorE == null) {
                motivos.add("Com embeddings LOCAL, escolha o provedor local do catálogo (ex.: "
                        + PROVEDOR_EMBEDDINGS_PADRAO + ").");
            } else {
                modeloE = validarProvedor(cat, provedorE, modeloE, FuncaoIa.EMBEDDINGS, true, motivos);
            }
        }
        if (!motivos.isEmpty()) {
            throw new ConfiguracaoIaRecusadaException(motivos);
        }

        byte[] cifrada = null;
        String finalNova = null;
        if (chave != null) {
            cifrada = CifradorChave.cifrar(chave, chavePublica(cat));
            finalNova = CifradorChave.finalDaChave(chave);
        }

        var respostasNovas = new Valores(pr.modo(), provedorR, modeloR);
        var embeddingsNovos = new Valores(pe.modo(), provedorE, modeloE);
        byte[] cifradaFinal = cifrada;
        String finalFinal = finalNova;
        transacao.executeWithoutResult(status -> aplicar(condominioId, pedido.modoGeral(), respostasNovas,
                cifradaFinal, finalFinal, pr.removerChave(), embeddingsNovos, usuario));
        return ler(condominioId);
    }

    /** Dentro da transação, com o condomínio travado: compara com o que vale, grava o que mudou e a trilha. */
    private void aplicar(UUID condominioId, ModoIa modoGeral, Valores respostas, byte[] chaveCifrada,
            String chaveFinal, boolean removerChave, Valores embeddings, String usuario) {
        configuracoes.serializarAlteracao(condominioId.toString());
        List<ConfiguracaoIa> linhas = configuracoes.findByCondominioId(condominioId);
        Instant agora = Instant.now();

        // Modo geral
        Optional<ConfiguracaoIa> geral = linha(linhas, null, FuncaoIa.RESPOSTAS);
        ModoIa geralAntes = geral.map(ConfiguracaoIa::getModo).orElse(MODO_GERAL_PADRAO);
        if (geralAntes != modoGeral) {
            ConfiguracaoIa l = geral.orElseGet(() -> new ConfiguracaoIa(condominioId, null, FuncaoIa.RESPOSTAS));
            l.alterar(modoGeral, null, null, usuario, agora);
            configuracoes.save(l);
            eventos.save(new EventoConfiguracaoIa(condominioId, null, FuncaoIa.RESPOSTAS, usuario, agora, geralAntes,
                    modoGeral, null, null, null, null, false, null));
        }

        // Respostas do Assistente
        Optional<ConfiguracaoIa> resp = linha(linhas, Modulos.ASSISTENTE, FuncaoIa.RESPOSTAS);
        Valores respAntes = resp.map(l -> new Valores(l.getModo(), l.getProvedor(), l.getModelo()))
                .orElse(new Valores(null, null, null));
        boolean tinhaChave = resp.map(ConfiguracaoIa::temChave).orElse(false);
        boolean chaveTrocada = chaveCifrada != null || (removerChave && tinhaChave);
        ModoIa efetivo = respostas.modo() == null ? modoGeral : respostas.modo();
        if (efetivo == ModoIa.API_KEY && chaveCifrada == null && (!tinhaChave || removerChave)) {
            // Outro Admin removeu a chave entre a validação e a gravação
            throw new ConfiguracaoIaRecusadaException(
                    List.of("O modo API_KEY exige a chave de API do condomínio: informe a chave."));
        }
        if (!respAntes.equals(respostas) || chaveTrocada) {
            ConfiguracaoIa l = resp.orElseGet(() -> new ConfiguracaoIa(condominioId, Modulos.ASSISTENTE,
                    FuncaoIa.RESPOSTAS));
            l.alterar(respostas.modo(), respostas.provedor(), respostas.modelo(), usuario, agora);
            if (chaveCifrada != null) {
                l.trocarChave(chaveCifrada, chaveFinal);
            } else if (removerChave) {
                l.trocarChave(null, null);
            }
            configuracoes.save(l);
            eventos.save(new EventoConfiguracaoIa(condominioId, Modulos.ASSISTENTE, FuncaoIa.RESPOSTAS, usuario, agora,
                    respAntes.modo(), respostas.modo(), respAntes.provedor(), respostas.provedor(), respAntes.modelo(),
                    respostas.modelo(), chaveTrocada, chaveCifrada != null ? chaveFinal : null));
        }

        // Embeddings do Assistente
        Optional<ConfiguracaoIa> emb = linha(linhas, Modulos.ASSISTENTE, FuncaoIa.EMBEDDINGS);
        Valores embAntes = emb.map(l -> new Valores(l.getModo(), l.getProvedor(), l.getModelo()))
                .orElse(new Valores(MODO_EMBEDDINGS_PADRAO, PROVEDOR_EMBEDDINGS_PADRAO, MODELO_EMBEDDINGS_PADRAO));
        if (!embAntes.equals(embeddings)) {
            ConfiguracaoIa l = emb.orElseGet(() -> new ConfiguracaoIa(condominioId, Modulos.ASSISTENTE,
                    FuncaoIa.EMBEDDINGS));
            l.alterar(embeddings.modo(), embeddings.provedor(), embeddings.modelo(), usuario, agora);
            configuracoes.save(l);
            eventos.save(new EventoConfiguracaoIa(condominioId, Modulos.ASSISTENTE, FuncaoIa.EMBEDDINGS, usuario,
                    agora, embAntes.modo(), embeddings.modo(), embAntes.provedor(), embeddings.provedor(),
                    embAntes.modelo(), embeddings.modelo(), false, null));
        }
        log.info("Configuração de IA do condomínio {} gravada por {} (modo geral {}, respostas {}, embeddings {}{})",
                condominioId, usuario, modoGeral, respostas.modo() == null ? "herda" : respostas.modo(),
                embeddings.modo(), chaveCifrada != null ? ", chave trocada" : removerChave ? ", chave removida" : "");
    }

    /** Confere provedor e modelo no catálogo; devolve o modelo resolvido (padrão do provedor quando nulo). */
    private static String validarProvedor(Catalogo cat, String provedor, String modelo, FuncaoIa uso,
            boolean exigeLocal, List<String> motivos) {
        Optional<ProvedorIa> p = cat.provedor(provedor);
        if (p.isEmpty()) {
            motivos.add("O provedor '" + provedor + "' não está no catálogo.");
            return modelo;
        }
        if (p.get().uso() != uso) {
            motivos.add("O provedor '" + provedor + "' não é de " + (uso == FuncaoIa.RESPOSTAS ? "respostas"
                    : "embeddings") + ".");
            return modelo;
        }
        if (exigeLocal && !p.get().local()) {
            motivos.add("O provedor '" + provedor + "' não é local: para embeddings só é aceito provedor local, sem"
                    + " enviar texto para fora (Q12).");
            return modelo;
        }
        if (modelo == null) {
            Optional<ModeloIa> padrao = p.get().modeloPadrao();
            if (padrao.isEmpty()) {
                motivos.add("O provedor '" + provedor + "' não tem modelos no catálogo.");
                return null;
            }
            return padrao.get().id();
        }
        if (p.get().modelo(modelo).isEmpty()) {
            motivos.add("O modelo '" + modelo + "' não está no catálogo do provedor '" + provedor + "'.");
        }
        return modelo;
    }

    private static PublicKey chavePublica(Catalogo cat) {
        if (cat == null || cat.chavePublicaPem() == null || cat.chavePublicaPem().isBlank()) {
            throw new IaIndisponivelException(SEM_CHAVE_PUBLICA);
        }
        try {
            return CifradorChave.chavePublica(cat.chavePublicaPem());
        } catch (IllegalArgumentException erro) {
            log.error("Chave pública do rag recusada: {}", erro.getMessage());
            throw new IaIndisponivelException(SEM_CHAVE_PUBLICA);
        }
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
