package br.com.condominioauditoria.api.assistente;

import br.com.condominioauditoria.api.assistente.DtosAssistente.PedidoBuscaDocumentos;
import br.com.condominioauditoria.api.assistente.DtosAssistente.TrechoDocumento;
import br.com.condominioauditoria.api.grpc.ClienteAssistente;
import br.com.condominioauditoria.api.modulo.Modulos;
import br.com.condominioauditoria.api.modulo.PedidoInvalidoException;
import br.com.condominioauditoria.api.modulo.RegistroUso;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarRequest;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarResponse;
import br.com.condominioauditoria.contratos.assistente.v1.FiltrosBusca;
import br.com.condominioauditoria.contratos.assistente.v1.ModoBusca;
import br.com.condominioauditoria.contratos.assistente.v1.Trecho;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Busca por palavra nos documentos pela tela (RF-04.18): sem IA, em qualquer modo de IA (inclusive DESLIGADO, Q7),
 * só com o módulo Assistente ligado. Sempre o modo PALAVRA do rag. Mesma segunda barreira do chat e registro de uso
 * "busca_documentos" (sem o texto buscado).
 */
@Service
class BuscaAssistente {

    private static final Logger log = LoggerFactory.getLogger(BuscaAssistente.class);
    static final int TEXTO_MAXIMO = 500;
    static final int LIMITE_PADRAO = 10;
    static final int LIMITE_MAXIMO = 50;
    static final String MSG_INDISPONIVEL = "Busca nos documentos indisponível no momento: o serviço rag não"
            + " respondeu. Tente de novo em instantes.";

    private final CondominiumAccess acesso;
    private final Modulos modulos;
    private final ClienteAssistente rag;
    private final BarreiraArquivos barreira;
    private final RegistroUso registroUso;

    BuscaAssistente(CondominiumAccess acesso, Modulos modulos, ClienteAssistente rag, BarreiraArquivos barreira,
            RegistroUso registroUso) {
        this.acesso = acesso;
        this.modulos = modulos;
        this.rag = rag;
        this.barreira = barreira;
        this.registroUso = registroUso;
    }

    List<TrechoDocumento> buscar(UUID condominioId, PedidoBuscaDocumentos pedido) {
        modulos.exigir(condominioId, Modulos.ASSISTENTE);
        BuscarRequest pedidoRag = montar(condominioId, pedido);
        String autorizacao = acesso.bearerToken().orElseThrow(() -> new IllegalStateException("Token ausente"));
        BuscarResponse resposta;
        try {
            resposta = rag.buscar(pedidoRag, autorizacao);
        } catch (StatusRuntimeException erro) {
            throw traduzir(erro);
        }
        Set<String> visiveis = barreira.visiveis(condominioId, resposta.getTrechosList());
        List<Trecho> permitidos = resposta.getTrechosList().stream()
                .filter(t -> BarreiraArquivos.permitido(t, visiveis)).toList();
        if (permitidos.size() < resposta.getTrechosCount()) {
            log.warn("Busca nos documentos: {} trecho(s) do rag descartado(s) pela segunda barreira (condomínio {})",
                    resposta.getTrechosCount() - permitidos.size(), condominioId);
        }
        registroUso.buscaDocumentos(condominioId, acesso.username());
        return permitidos.stream().map(TrechoDocumento::de).toList();
    }

    static BuscarRequest montar(UUID condominioId, PedidoBuscaDocumentos pedido) {
        String texto = pedido == null || pedido.texto() == null ? "" : pedido.texto().strip();
        if (texto.isEmpty()) {
            throw new PedidoInvalidoException("Informe o texto da busca");
        }
        if (texto.length() > TEXTO_MAXIMO) {
            throw new PedidoInvalidoException("O texto da busca passa de " + TEXTO_MAXIMO + " caracteres");
        }
        int limite = pedido.limite() == null ? LIMITE_PADRAO : pedido.limite();
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new PedidoInvalidoException("O limite deve ser de 1 a " + LIMITE_MAXIMO);
        }
        var construtor = BuscarRequest.newBuilder()
                .setCondominioId(condominioId.toString())
                .setTexto(texto)
                .setModo(ModoBusca.MODO_BUSCA_PALAVRA)
                .setLimite(limite);
        FiltrosBusca filtros = PedidosRag.filtros(pedido.filtros());
        if (filtros != null) {
            construtor.setFiltros(filtros);
        }
        return construtor.build();
    }

    private static RuntimeException traduzir(StatusRuntimeException erro) {
        Status status = erro.getStatus();
        if (status.getCode() == Status.Code.INVALID_ARGUMENT) {
            return new PedidoInvalidoException(Objects.requireNonNullElse(status.getDescription(),
                    "Pedido de busca inválido"));
        }
        log.warn("Busca nos documentos: rag respondeu {} ({})", status.getCode(), status.getDescription());
        return new RecusaAssistenteException(HttpStatus.SERVICE_UNAVAILABLE, "Busca indisponível", MSG_INDISPONIVEL,
                null);
    }
}
