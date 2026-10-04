package br.com.condominioauditoria.backend.erro;

import br.com.condominioauditoria.backend.arquivo.ArquivoService.ArquivoDuplicadoException;
import br.com.condominioauditoria.backend.modulo.ModuloDesconhecidoException;
import br.com.condominioauditoria.backend.modulo.ModuloNaoContratadoException;
import br.com.condominioauditoria.backend.modulo.PedidoInvalidoException;
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
}
