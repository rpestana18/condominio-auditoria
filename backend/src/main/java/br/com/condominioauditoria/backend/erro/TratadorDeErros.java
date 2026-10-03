package br.com.condominioauditoria.backend.erro;

import br.com.condominioauditoria.backend.arquivo.ArquivoService.ArquivoDuplicadoException;
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
}
