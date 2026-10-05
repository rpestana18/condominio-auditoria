package br.com.condominioauditoria.backend.modulo;

import br.com.condominioauditoria.backend.modulo.CatalogoModulos.DefinicaoModulo;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verificação central dos módulos contratáveis (RF-10; ADR 0003, Decisão 4). Todo ponto que pertence a um módulo
 * chama {@link #exigir} (recusa) ou {@link #ligado} (pula em silêncio, ex.: indexação). O estado vem do banco a cada
 * chamada: ligar ou desligar vale no próximo pedido, sem reinício (RF-10.2).
 *
 * Quem altera é o perfil ADMIN, verificado na API (ModuloController). No MVP o ADMIN do Keycloak é o administrador da
 * plataforma ("tudo, em todos os condomínios", ver AcessoCondominio) e faz o papel do Super-admin do RF-10.2. Quando
 * os perfis por condomínio existirem, o Admin do condomínio deixa de poder alterar e só o Super-admin altera.
 */
@Service
public class Modulos {

    /** Código do módulo Assistente no catálogo (catalogo-modulos.yml). */
    public static final String ASSISTENTE = "ASSISTENTE";
    static final int MOTIVO_MAXIMO = 500;

    private static final Logger log = LoggerFactory.getLogger(Modulos.class);

    private final CatalogoModulos catalogo;
    private final ModuloCondominioRepository estados;
    private final EventoModuloRepository eventos;
    private final ApplicationEventPublisher publicador;

    Modulos(CatalogoModulos catalogo, ModuloCondominioRepository estados, EventoModuloRepository eventos,
            ApplicationEventPublisher publicador) {
        this.catalogo = catalogo;
        this.estados = estados;
        this.eventos = eventos;
        this.publicador = publicador;
        if (catalogo.buscar(ASSISTENTE).isEmpty()) {
            throw new IllegalStateException("O catálogo de módulos não tem o " + ASSISTENTE);
        }
    }

    /** Estado de um módulo do catálogo no condomínio, com o que ele inclui. */
    public record EstadoModulo(DefinicaoModulo definicao, boolean ligado, Instant desde, int versaoCatalogo) {
    }

    public CatalogoModulos catalogo() {
        return catalogo;
    }

    /** Ligado no condomínio? Sem linha no banco = padrão do catálogo. Módulo fora do catálogo nunca está ligado. */
    public boolean ligado(UUID condominioId, String modulo) {
        Optional<DefinicaoModulo> definicao = catalogo.buscar(modulo);
        if (definicao.isEmpty()) {
            return false;
        }
        return estados.findById(new ModuloCondominio.Chave(condominioId, modulo))
                .map(ModuloCondominio::isLigado)
                .orElse(definicao.get().ligadoPorPadrao());
    }

    /** Recusa com "Módulo X não contratado para este condomínio." se o módulo está desligado (RF-10.3). */
    public void exigir(UUID condominioId, String modulo) {
        if (!ligado(condominioId, modulo)) {
            DefinicaoModulo definicao = catalogo.exigir(modulo);
            throw new ModuloNaoContratadoException(definicao.codigo(), definicao.nome());
        }
    }

    /** Todos os módulos do catálogo, na ordem do catálogo, com o estado no condomínio. */
    @Transactional(readOnly = true)
    public List<EstadoModulo> estados(UUID condominioId) {
        Map<String, ModuloCondominio> gravados = estados.doCondominio(condominioId).stream()
                .collect(Collectors.toMap(ModuloCondominio::getModulo, Function.identity()));
        return catalogo.modulos().stream().map(d -> estado(d, Optional.ofNullable(gravados.get(d.codigo())))).toList();
    }

    /** Códigos dos módulos ligados no condomínio, na ordem do catálogo. */
    public List<String> ligados(UUID condominioId) {
        return estados(condominioId).stream().filter(EstadoModulo::ligado).map(e -> e.definicao().codigo()).toList();
    }

    /**
     * Liga ou desliga (RF-10.2) e grava o evento na trilha com o motivo, opcional (RF-10.6). Pedir o estado que já
     * vale não grava nada. Ligar o Assistente reindexa os arquivos do condomínio depois do commit (RF-10.4, por quem
     * escuta {@link ModuloAlterado}); desligar não apaga índice nem originais (RF-10.5).
     */
    @Transactional
    public EstadoModulo alterar(UUID condominioId, String modulo, boolean ligar, String motivo, String usuario) {
        DefinicaoModulo definicao = catalogo.exigir(modulo);
        String motivoLimpo = motivo == null || motivo.isBlank() ? null : motivo.strip();
        if (motivoLimpo != null && motivoLimpo.length() > MOTIVO_MAXIMO) {
            throw new PedidoInvalidoException("O motivo passa de " + MOTIVO_MAXIMO + " caracteres");
        }
        var chave = new ModuloCondominio.Chave(condominioId, modulo);
        estados.serializarAlteracao(condominioId.toString(), modulo);
        Optional<ModuloCondominio> atual = estados.travar(chave);
        boolean antes = atual.map(ModuloCondominio::isLigado).orElse(definicao.ligadoPorPadrao());
        if (antes == ligar) {
            return estado(definicao, atual);
        }
        Instant agora = Instant.now();
        ModuloCondominio linha = atual.orElseGet(() -> new ModuloCondominio(condominioId, modulo, ligar, agora, usuario));
        linha.alterar(ligar, agora, usuario);
        linha = estados.save(linha);
        eventos.save(new EventoModulo(condominioId, modulo, antes, ligar, usuario, agora, motivoLimpo));
        publicador.publishEvent(new ModuloAlterado(condominioId, modulo, ligar, usuario));
        log.info("Módulo {} {} no condomínio {} por {} (motivo: {})", modulo, ligar ? "ligado" : "desligado",
                condominioId, usuario, motivoLimpo == null ? "não informado" : motivoLimpo);
        return estado(definicao, Optional.of(linha));
    }

    /** Trilha de ativação do módulo no condomínio, da mais antiga para a mais recente. */
    @Transactional(readOnly = true)
    public List<EventoModulo> eventos(UUID condominioId, String modulo) {
        catalogo.exigir(modulo);
        return eventos.findByCondominioIdAndModuloOrderByQuandoAscIdAsc(condominioId, modulo);
    }

    /** Períodos ativos calculados da trilha (RF-10.6). */
    @Transactional(readOnly = true)
    public List<PeriodoAtivo> periodos(UUID condominioId, String modulo) {
        DefinicaoModulo definicao = catalogo.exigir(modulo);
        return PeriodoAtivo.calcular(modulo, definicao.ligadoPorPadrao(),
                eventos.findByCondominioIdAndModuloOrderByQuandoAscIdAsc(condominioId, modulo));
    }

    private EstadoModulo estado(DefinicaoModulo definicao, Optional<ModuloCondominio> gravado) {
        return new EstadoModulo(definicao, gravado.map(ModuloCondominio::isLigado).orElse(definicao.ligadoPorPadrao()),
                gravado.map(ModuloCondominio::getDesde).orElse(null), catalogo.versao());
    }
}
