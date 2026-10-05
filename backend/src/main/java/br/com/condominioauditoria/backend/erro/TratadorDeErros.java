package br.com.condominioauditoria.backend.erro;

import br.com.condominioauditoria.backend.arquivo.ArquivoService.ArquivoDuplicadoException;
import br.com.condominioauditoria.backend.assistente.RecusaAssistenteException;
import br.com.condominioauditoria.backend.ia.ConfiguracaoIaRecusadaException;
import br.com.condominioauditoria.backend.ia.IaIndisponivelException;
import br.com.condominioauditoria.backend.modulo.ModuloDesconhecidoException;
import br.com.condominioauditoria.backend.modulo.ModuloNaoContratadoException;
import br.com.condominioauditoria.backend.modulo.PedidoInvalidoException;
import br.com.condominioauditoria.backend.orcamento.ConfirmacaoRecusadaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Erros da API em formato padrão (RFC 9457), com mensagens em português. */
@RestControllerAdvice
class TratadorDeErros {

    @ExceptionHandler(ArquivoDuplicadoException.class)
    ProblemDetail duplicado(ArquivoDuplicadoException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        p.setTitle("Arquivo já enviado");
        p.setProperty("arquivoExistenteId", e.existente().getId());
        return p;
    }

    /** Todos os motivos de uma vez, para a tela mostrar o que falta. */
    @ExceptionHandler(ConfirmacaoRecusadaException.class)
    ProblemDetail confirmacaoRecusada(ConfirmacaoRecusadaException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        p.setTitle("Confirmação recusada");
        p.setProperty("motivos", e.motivos());
        return p;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail grandeDemais(MaxUploadSizeExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "O arquivo passa do limite de 50 MB");
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail estadoInvalido(IllegalStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Ponto de um módulo desligado no condomínio (RF-10.3): "Módulo Assistente não contratado para este condomínio." */
    @ExceptionHandler(ModuloNaoContratadoException.class)
    ProblemDetail moduloNaoContratado(ModuloNaoContratadoException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        p.setTitle("Módulo não contratado");
        p.setProperty("modulo", e.modulo());
        return p;
    }

    @ExceptionHandler(ModuloDesconhecidoException.class)
    ProblemDetail moduloDesconhecido(ModuloDesconhecidoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(PedidoInvalidoException.class)
    ProblemDetail pedidoInvalido(PedidoInvalidoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Configuração de IA recusada (RF-09.6): 422 com todos os motivos. Nenhum motivo leva a chave. */
    @ExceptionHandler(ConfiguracaoIaRecusadaException.class)
    ProblemDetail configuracaoIaRecusada(ConfiguracaoIaRecusadaException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        p.setTitle("Configuração de IA recusada");
        p.setProperty("motivos", e.motivos());
        return p;
    }

    /** Rag fora do ar ou sem chave pública ao ler o catálogo ou gravar a chave: 503, nada gravado. */
    @ExceptionHandler(IaIndisponivelException.class)
    ProblemDetail iaIndisponivel(IaIndisponivelException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        p.setTitle("Serviço de IA indisponível");
        return p;
    }

    /** Chat e busca do Assistente: 409 pelo modo (com modoIa), 422, 429, 503 e 504 conforme o contrato. */
    @ExceptionHandler(RecusaAssistenteException.class)
    ProblemDetail recusaAssistente(RecusaAssistenteException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        p.setTitle(e.titulo());
        if (e.modoIa() != null) {
            p.setProperty("modoIa", e.modoIa().name());
        }
        return p;
    }
}
