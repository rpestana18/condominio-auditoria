package br.com.condominioauditoria.backend.grpc;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import br.com.condominioauditoria.backend.arquivo.ArquivoRepository;
import br.com.condominioauditoria.backend.arquivo.Categoria;
import br.com.condominioauditoria.backend.condominio.CondominioRepository;
import br.com.condominioauditoria.backend.contabil.ConferenciaRepository;
import br.com.condominioauditoria.backend.contabil.Fundo;
import br.com.condominioauditoria.backend.contabil.FundoRepository;
import br.com.condominioauditoria.backend.contabil.LancamentoRepository;
import br.com.condominioauditoria.backend.modulo.ModuloNaoContratadoException;
import br.com.condominioauditoria.backend.painel.PainelService;
import br.com.condominioauditoria.backend.seguranca.AcessoCondominio;
import br.com.condominioauditoria.contratos.consulta.v1.ArquivoResumo;
import br.com.condominioauditoria.contratos.consulta.v1.BuscarDocumentosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.BuscarDocumentosResponse;
import br.com.condominioauditoria.contratos.consulta.v1.CondominioResumo;
import br.com.condominioauditoria.contratos.consulta.v1.Conferencia;
import br.com.condominioauditoria.contratos.consulta.v1.ConferenciasDoArquivoRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ConferenciasDoArquivoResponse;
import br.com.condominioauditoria.contratos.consulta.v1.ConsultaGrpc;
import br.com.condominioauditoria.contratos.consulta.v1.FundoNoPeriodo;
import br.com.condominioauditoria.contratos.consulta.v1.Lancamento;
import br.com.condominioauditoria.contratos.consulta.v1.ListarArquivosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ListarArquivosResponse;
import br.com.condominioauditoria.contratos.consulta.v1.ListarCondominiosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ListarCondominiosResponse;
import br.com.condominioauditoria.contratos.consulta.v1.ListarLancamentosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ResumoFundosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ResumoFundosResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Implementação do contrato contracts/grpc/consulta/v1/consulta.proto. Só leitura. Usa os mesmos serviços e
 * repositórios da API REST, então os números batem com a tela.
 */
@Component
class ConsultaGrpcServico extends ConsultaGrpc.ConsultaImplBase {

    private static final Logger log = LoggerFactory.getLogger(ConsultaGrpcServico.class);
    private static final int LIMITE_PADRAO = 500;
    private static final int LIMITE_MAXIMO = 5000;

    private final AcessoCondominio acesso;
    private final CondominioRepository condominios;
    private final ArquivoRepository arquivos;
    private final ConferenciaRepository conferencias;
    private final FundoRepository fundos;
    private final LancamentoRepository lancamentos;
    private final PainelService painel;
    private final BuscaDocumentos buscaDocumentos;

    ConsultaGrpcServico(AcessoCondominio acesso, CondominioRepository condominios, ArquivoRepository arquivos,
            ConferenciaRepository conferencias, FundoRepository fundos, LancamentoRepository lancamentos,
            PainelService painel, BuscaDocumentos buscaDocumentos) {
        this.acesso = acesso;
        this.condominios = condominios;
        this.arquivos = arquivos;
        this.conferencias = conferencias;
        this.fundos = fundos;
        this.lancamentos = lancamentos;
        this.painel = painel;
        this.buscaDocumentos = buscaDocumentos;
    }

    @Override
    public void listarCondominios(ListarCondominiosRequest pedido, StreamObserver<ListarCondominiosResponse> resposta) {
        responder(resposta, () -> ListarCondominiosResponse.newBuilder()
                .addAllCondominios(condominios.findAll().stream()
                        .filter(c -> acesso.podeAcessar(c.getId()))
                        .map(c -> CondominioResumo.newBuilder().setId(c.getId().toString()).setNome(c.getNome()).build())
                        .toList())
                .build());
    }

    @Override
    public void resumoFundos(ResumoFundosRequest pedido, StreamObserver<ResumoFundosResponse> resposta) {
        responder(resposta, () -> {
            UUID condominioId = condominio(pedido.getCondominioId());
            return painel.ultimo(condominioId).map(p -> ResumoFundosResponse.newBuilder()
                    .setTemDados(true)
                    .setArquivoId(p.arquivoId().toString())
                    .setArquivoNome(p.arquivoNome())
                    .setPeriodoInicio(texto(p.periodoInicio()))
                    .setPeriodoFim(texto(p.periodoFim()))
                    .setSaldoAnterior(dinheiro(p.saldoAnterior()))
                    .setEntradas(dinheiro(p.entradas()))
                    .setSaidas(dinheiro(p.saidas()))
                    .setSaldoAtual(dinheiro(p.saldoAtual()))
                    .setConferenciasComFalha((int) p.conferenciasComFalha())
                    .addAllFundos(p.fundos().stream().map(f -> FundoNoPeriodo.newBuilder()
                            .setFundo(f.fundo())
                            .setSaldoAnterior(dinheiro(f.saldoAnterior()))
                            .setEntradas(dinheiro(f.entradas()))
                            .setSaidas(dinheiro(f.saidas()))
                            .setSaldoAtual(dinheiro(f.saldoAtual()))
                            .build()).toList())
                    .build())
                    .orElse(ResumoFundosResponse.newBuilder().setTemDados(false).build());
        });
    }

    @Override
    public void listarArquivos(ListarArquivosRequest pedido, StreamObserver<ListarArquivosResponse> resposta) {
        responder(resposta, () -> {
            UUID condominioId = condominio(pedido.getCondominioId());
            int limite = limite(pedido.getLimite(), 50, 500);
            List<Arquivo> lista = pedido.getCategoria().isBlank()
                    ? arquivos.findByCondominioIdOrderByEnviadoEmDesc(condominioId)
                    : arquivos.findByCondominioIdAndCategoriaOrderByEnviadoEmDesc(condominioId, categoria(pedido.getCategoria()));
            return ListarArquivosResponse.newBuilder()
                    .addAllArquivos(lista.stream().limit(limite).map(ConsultaGrpcServico::resumo).toList())
                    .build();
        });
    }

    @Override
    public void conferenciasDoArquivo(ConferenciasDoArquivoRequest pedido,
            StreamObserver<ConferenciasDoArquivoResponse> resposta) {
        responder(resposta, () -> {
            UUID condominioId = condominio(pedido.getCondominioId());
            UUID arquivoId = uuid(pedido.getArquivoId(), "arquivo_id");
            arquivos.findByIdAndCondominioId(arquivoId, condominioId)
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("Arquivo não encontrado").asRuntimeException());
            return ConferenciasDoArquivoResponse.newBuilder()
                    .addAllConferencias(conferencias.findByArquivoIdOrderByOrdem(arquivoId).stream()
                            .map(c -> Conferencia.newBuilder().setCodigo(c.getCodigo()).setDescricao(c.getDescricao())
                                    .setOk(c.isOk()).setDetalhe(Objects.toString(c.getDetalhe(), "")).build())
                            .toList())
                    .build();
        });
    }

    /** Resposta em fluxo: cada lançamento sai assim que é convertido, sem montar a lista inteira no cliente. */
    @Override
    public void listarLancamentos(ListarLancamentosRequest pedido, StreamObserver<Lancamento> resposta) {
        try {
            UUID condominioId = condominio(pedido.getCondominioId());
            Map<UUID, String> nomes = fundos.findByCondominioId(condominioId).stream()
                    .collect(Collectors.toMap(Fundo::getId, Fundo::getNome));
            String filtroFundo = pedido.getFundo().trim().toLowerCase();
            List<UUID> fundosFiltrados = filtroFundo.isEmpty() ? List.of(UUID.randomUUID())
                    : nomes.entrySet().stream().filter(e -> e.getValue().toLowerCase().contains(filtroFundo))
                            .map(Map.Entry::getKey).toList();
            if (!filtroFundo.isEmpty() && fundosFiltrados.isEmpty()) {
                resposta.onCompleted();
                return;
            }
            String texto = pedido.getTexto().isBlank() ? "" : "%" + pedido.getTexto().trim().toLowerCase() + "%";
            var lista = lancamentos.filtrar(condominioId,
                    data(pedido.getDataInicio(), LocalDate.of(1900, 1, 1)),
                    data(pedido.getDataFim(), LocalDate.of(9999, 12, 31)),
                    filtroFundo.isEmpty(), fundosFiltrados, texto, pedido.getSomenteSaidas(),
                    Limit.of(limite(pedido.getLimite(), LIMITE_PADRAO, LIMITE_MAXIMO)));
            for (var l : lista) {
                resposta.onNext(Lancamento.newBuilder()
                        .setData(texto(l.getData()))
                        .setFundo(Objects.toString(nomes.get(l.getFundoId()), ""))
                        .setContaCodigo(Objects.toString(l.getContaCodigo(), ""))
                        .setContaNome(Objects.toString(l.getContaNome(), ""))
                        .setDocumento(Objects.toString(l.getDocumento(), ""))
                        .setHistorico(l.getHistorico())
                        .setCredito(dinheiro(l.getCredito()))
                        .setDebito(dinheiro(l.getDebito()))
                        .setSaldo(dinheiro(l.getSaldo()))
                        .setFornecedor(Objects.toString(l.getFornecedor(), ""))
                        .setNotaFiscal(Objects.toString(l.getNotaFiscal(), ""))
                        .setMeioPagamento(Objects.toString(l.getMeioPagamento(), ""))
                        .setTransferenciaEntreFundos(l.isTransferenciaEntreFundos())
                        .setArquivoId(l.getArquivoId().toString())
                        .setPagina(l.getPagina())
                        .build());
            }
            resposta.onCompleted();
        } catch (RuntimeException erro) {
            resposta.onError(traduzir(erro));
        }
    }

    /**
     * Busca trechos nos documentos do condomínio, pelo rag (ADR 0003, Decisão 5.3). Mesma verificação de token, perfil
     * e condomínio dos outros rpcs; o rag ainda filtra pelo condomínio e o backend descarta, na volta, trechos de
     * arquivos que não são do condomínio.
     */
    @Override
    public void buscarDocumentos(BuscarDocumentosRequest pedido, StreamObserver<BuscarDocumentosResponse> resposta) {
        responder(resposta, () -> buscaDocumentos.buscar(condominio(pedido.getCondominioId()), pedido));
    }

    // ---- apoio ----

    private <T> void responder(StreamObserver<T> resposta, java.util.function.Supplier<T> acao) {
        try {
            resposta.onNext(acao.get());
            resposta.onCompleted();
        } catch (RuntimeException erro) {
            resposta.onError(traduzir(erro));
        }
    }

    private StatusRuntimeException traduzir(RuntimeException erro) {
        return switch (erro) {
            case StatusRuntimeException s -> s;
            case AccessDeniedException a -> Status.PERMISSION_DENIED.withDescription(a.getMessage()).asRuntimeException();
            case ModuloNaoContratadoException m ->
                    Status.FAILED_PRECONDITION.withDescription(m.getMessage()).asRuntimeException();
            case IllegalArgumentException i -> Status.INVALID_ARGUMENT.withDescription(i.getMessage()).asRuntimeException();
            default -> {
                log.error("Erro no gRPC de consulta", erro);
                yield Status.INTERNAL.withDescription("Erro interno no backend").asRuntimeException();
            }
        };
    }

    private UUID condominio(String id) {
        UUID condominioId = uuid(id, "condominio_id");
        acesso.exigir(condominioId);
        return condominioId;
    }

    private static UUID uuid(String valor, String campo) {
        try {
            return UUID.fromString(valor.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(campo + " inválido: '" + valor + "'");
        }
    }

    private static Categoria categoria(String valor) {
        try {
            return Categoria.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Categoria desconhecida: " + valor);
        }
    }

    private static LocalDate data(String valor, LocalDate padrao) {
        if (valor.isBlank()) {
            return padrao;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida (use AAAA-MM-DD): " + valor);
        }
    }

    private static int limite(int pedido, int padrao, int maximo) {
        return pedido <= 0 ? padrao : Math.min(pedido, maximo);
    }

    private static String dinheiro(BigDecimal valor) {
        return valor == null ? "" : valor.setScale(2).toPlainString();
    }

    private static String texto(Object valor) {
        return valor == null ? "" : valor.toString();
    }

    private static ArquivoResumo resumo(Arquivo a) {
        return ArquivoResumo.newBuilder()
                .setId(a.getId().toString())
                .setCategoria(a.getCategoria().name())
                .setNome(a.getNomeOriginal())
                .setStatus(a.getStatus().name())
                .setMensagem(Objects.toString(a.getMensagem(), ""))
                .setPeriodoInicio(texto(a.getPeriodoInicio()))
                .setPeriodoFim(texto(a.getPeriodoFim()))
                .setTotalLancamentos(a.getTotalLancamentos() == null ? 0 : a.getTotalLancamentos())
                .setEnviadoPor(a.getEnviadoPor())
                .setEnviadoEm(texto(a.getEnviadoEm()))
                .build();
    }
}
