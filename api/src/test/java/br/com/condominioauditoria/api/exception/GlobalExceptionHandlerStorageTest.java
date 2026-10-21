package br.com.condominioauditoria.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.AccessDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

/** Write denied in the data folder: 503 with the message in Portuguese, never a generic 500. */
class GlobalExceptionHandlerStorageTest {

    @Test
    void writeDeniedBecomes503WithMessage() {
        ProblemDetail p = new GlobalExceptionHandler()
                .storageWriteDenied(new StorageWriteDeniedException(new AccessDeniedException("/dados/x")));

        assertThat(p.getStatus()).isEqualTo(503);
        assertThat(p.getTitle()).isEqualTo("Armazenamento indisponível");
        assertThat(p.getDetail()).contains("permissão negada").contains("Nada foi salvo").doesNotContain("/dados");
    }
}
