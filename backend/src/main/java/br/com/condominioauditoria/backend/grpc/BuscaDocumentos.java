package br.com.condominioauditoria.backend.grpc;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import br.com.condominioauditoria.backend.arquivo.ArquivoRepository;
import br.com.condominioauditoria.backend.arquivo.Categoria;
import br.com.condominioauditoria.backend.seguranca.AcessoCondominio;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarRequest;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarResponse;
import br.com.condominioauditoria.contratos.assistente.v1.FiltrosBusca;
import br.com.condominioauditoria.contratos.assistente.v1.Localizacao;
import br.com.condominioauditoria.contratos.assistente.v1.ModoBusca;
import br.com.condominioauditoria.contratos.assistente.v1.Trecho;
import br.com.condominioauditoria.contratos.consulta.v1.BuscarDocumentosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.BuscarDocumentosResponse;
import br.com.condominioauditoria.contratos.consulta.v1.FiltrosDocumentos;
import br.com.condominioauditoria.contratos.consulta.v1.LocalPagina;
import br.com.condominioauditoria.contratos.consulta.v1.LocalParagrafos;
import br.com.condominioauditoria.contratos.consulta.v1.LocalPlanilha;
import br.com.condominioauditoria.contratos.consulta.v1.LocalizacaoTrecho;
import br.com.condominioauditoria.contratos.consulta.v1.ModoBuscaDocumentos;
import br.com.condominioauditoria.contratos.consulta.v1.TrechoDocumento;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * rpc BuscarDocumentos (contracts/grpc/consulta/v1, ADR 0003, Decisão 5.3): valida o pedido, repassa ao rag
 * (Assistente.Buscar) com o token do usuário e, na volta, descarta trechos de arquivos que não existem no backend
 * para o condomínio pedido (segunda barreira, além do filtro por condomínio que o rag já faz).
 *
 * Entrega 1: sempre pede o modo HIBRIDA (o rag cai para PALAVRA se os embeddings estiverem fora). A escolha do modo
 * pela configuração de IA e a verificação do módulo Assistente entram na entrega 2.
 */
@Component
class BuscaDocumentos {

    private static final Logger log = LoggerFactory.getLogger(BuscaDocumentos.class);
    static final int LIMITE_PADRAO = 10;
    static final int LIMITE_MAXIMO = 50;

    private final AcessoCondominio acesso;
    private final ArquivoRepository arquivos;
    private final ClienteAssistente rag;

    BuscaDocumentos(AcessoCondominio acesso, ArquivoRepository arquivos, ClienteAssistente rag) {
        this.acesso = acesso;
        this.arquivos = arquivos;
        this.rag = rag;
    }

    /** Chamado dentro do rpc, já com o usuário do token no contexto de segurança. */
    BuscarDocumentosResponse buscar(UUID condominioId, BuscarDocumentosRequest pedido) {
        BuscarRequest pedidoRag = paraRag(condominioId, pedido);
        String autorizacao = acesso.tokenBearer()
                .orElseThrow(() -> Status.UNAUTHENTICATED.withDescription("Token ausente").asRuntimeException());
        BuscarResponse resposta;
        try {
            resposta = rag.buscar(pedidoRag, autorizacao);
        } catch (StatusRuntimeException erro) {
            throw traduzirErroDoRag(erro);
        }
        return BuscarDocumentosResponse.newBuilder()
                .addAllTrechos(permitidos(condominioId, resposta.getTrechosList()).stream()
                        .map(BuscaDocumentos::converter).toList())
                .setModoUsado(switch (resposta.getModoUsado()) {
                    case MODO_BUSCA_PALAVRA -> ModoBuscaDocumentos.MODO_BUSCA_DOCUMENTOS_PALAVRA;
                    case MODO_BUSCA_HIBRIDA -> ModoBuscaDocumentos.MODO_BUSCA_DOCUMENTOS_HIBRIDA;
                    default -> ModoBuscaDocumentos.MODO_BUSCA_DOCUMENTOS_NAO_INFORMADO;
                })
                .build();
    }

    /** Validações do contrato: texto obrigatório, limite não negativo (0 = 10, acima de 50 = 50), datas ISO. */
    static BuscarRequest paraRag(UUID condominioId, BuscarDocumentosRequest pedido) {
        String texto = pedido.getTexto().strip();
        if (texto.isEmpty()) {
            throw new IllegalArgumentException("Informe o texto da busca");
        }
        if (pedido.getLimite() < 0) {
            throw new IllegalArgumentException("Limite não pode ser negativo: " + pedido.getLimite());
        }
        int limite = pedido.getLimite() == 0 ? LIMITE_PADRAO : Math.min(pedido.getLimite(), LIMITE_MAXIMO);
        var construtor = BuscarRequest.newBuilder()
                .setCondominioId(condominioId.toString())
                .setTexto(texto)
                .setModo(ModoBusca.MODO_BUSCA_HIBRIDA)
                .setLimite(limite);
        if (pedido.hasFiltros()) {
            construtor.setFiltros(filtros(pedido.getFiltros()));
        }
        return construtor.build();
    }

    private static FiltrosBusca filtros(FiltrosDocumentos f) {
        String inicio = data(f.getDataInicio());
        String fim = data(f.getDataFim());
        if (!inicio.isEmpty() && !fim.isEmpty() && inicio.compareTo(fim) > 0) {
            throw new IllegalArgumentException("Data inicial depois da final: " + inicio + " a " + fim);
        }
        return FiltrosBusca.newBuilder()
                .addAllCategorias(f.getCategoriasList().stream().map(BuscaDocumentos::categoria).distinct().toList())
                .setDataInicio(inicio)
                .setDataFim(fim)
                .addAllArquivoIds(f.getArquivoIdsList().stream().map(BuscaDocumentos::arquivoId).distinct().toList())
                .build();
    }

    /** Segunda barreira: só ficam trechos de arquivos que existem no backend e são do condomínio pedido. */
    private List<Trecho> permitidos(UUID condominioId, List<Trecho> trechos) {
        Set<UUID> citados = new HashSet<>();
        for (Trecho t : trechos) {
            uuidOpcional(t.getArquivoId()).ifPresent(citados::add);
        }
        Set<String> doCondominio = citados.isEmpty() ? Set.of()
                : arquivos.findByCondominioIdAndIdIn(condominioId, citados).stream()
                        .map(Arquivo::getId).map(UUID::toString).collect(Collectors.toSet());
        List<Trecho> lista = trechos.stream()
                .filter(t -> uuidOpcional(t.getArquivoId()).map(UUID::toString).filter(doCondominio::contains).isPresent())
                .toList();
        if (lista.size() < trechos.size()) {
            log.warn("Busca nos documentos: {} trecho(s) do rag descartado(s) por arquivo fora do condomínio {}",
                    trechos.size() - lista.size(), condominioId);
        }
        return lista;
    }

    static TrechoDocumento converter(Trecho t) {
        return TrechoDocumento.newBuilder()
                .setTrechoId(t.getTrechoId())
                .setArquivoId(t.getArquivoId())
                .setNomeArquivo(t.getNomeArquivo())
                .setCategoria(t.getCategoria())
                .setLocalizacao(localizacao(t.getLocalizacao()))
                .setTexto(t.getTexto())
                .setPontuacao(t.getPontuacao())
                .setSha256(t.getSha256())
                .build();
    }

    private static LocalizacaoTrecho localizacao(Localizacao l) {
        var construtor = LocalizacaoTrecho.newBuilder();
        switch (l.getTipoCase()) {
            case PAGINA -> construtor.setPagina(LocalPagina.newBuilder().setPagina(l.getPagina().getPagina()));
            case PLANILHA -> construtor.setPlanilha(LocalPlanilha.newBuilder()
                    .setAba(l.getPlanilha().getAba())
                    .setLinhaInicio(l.getPlanilha().getLinhaInicio())
                    .setLinhaFim(l.getPlanilha().getLinhaFim()));
            case PARAGRAFOS -> construtor.setParagrafos(LocalParagrafos.newBuilder()
                    .setParagrafoInicio(l.getParagrafos().getParagrafoInicio())
                    .setParagrafoFim(l.getParagrafos().getParagrafoFim())
                    .setSecao(l.getParagrafos().getSecao()));
            case TIPO_NOT_SET -> {
            }
        }
        return construtor.build();
    }

    /** Erro do rag em português, com o código que o contrato de consulta promete ao mcp. */
    private static StatusRuntimeException traduzirErroDoRag(StatusRuntimeException erro) {
        Status status = erro.getStatus();
        return switch (status.getCode()) {
            case INVALID_ARGUMENT -> Status.INVALID_ARGUMENT
                    .withDescription(Objects.requireNonNullElse(status.getDescription(), "Pedido de busca inválido"))
                    .asRuntimeException();
            case UNAVAILABLE, DEADLINE_EXCEEDED, UNIMPLEMENTED, CANCELLED -> {
                log.warn("Busca nos documentos: rag indisponível ({}: {})", status.getCode(), status.getDescription());
                yield Status.UNAVAILABLE
                        .withDescription("Busca nos documentos indisponível no momento: o serviço rag não respondeu."
                                + " Tente de novo em instantes.")
                        .asRuntimeException();
            }
            default -> {
                log.error("Busca nos documentos: erro no rag ({}: {})", status.getCode(), status.getDescription(), erro);
                yield Status.INTERNAL.withDescription("Erro no serviço de busca (rag)").asRuntimeException();
            }
        };
    }

    private static String categoria(String valor) {
        try {
            return Categoria.valueOf(valor.strip().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Categoria desconhecida: " + valor);
        }
    }

    private static String data(String valor) {
        if (valor.isBlank()) {
            return "";
        }
        try {
            return LocalDate.parse(valor.strip()).toString();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida (use AAAA-MM-DD): " + valor);
        }
    }

    private static String arquivoId(String valor) {
        return uuidOpcional(valor)
                .orElseThrow(() -> new IllegalArgumentException("arquivo_id inválido: '" + valor + "'"))
                .toString();
    }

    private static Optional<UUID> uuidOpcional(String valor) {
        try {
            return Optional.of(UUID.fromString(valor.strip()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
